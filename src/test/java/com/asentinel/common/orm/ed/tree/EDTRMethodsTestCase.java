package com.asentinel.common.orm.ed.tree;

import com.asentinel.common.collections.tree.Node;
import com.asentinel.common.collections.tree.SimpleNode;
import com.asentinel.common.jdbc.ConversionSupport;
import com.asentinel.common.jdbc.SqlQuery;
import com.asentinel.common.orm.CacheEntityDescriptor;
import com.asentinel.common.orm.Entity;
import com.asentinel.common.orm.EntityDescriptor;
import com.asentinel.common.orm.RelationType;
import com.asentinel.common.orm.SimpleEntityDescriptor;
import com.asentinel.common.orm.jql.SqlBuilderFactory;
import com.asentinel.common.orm.mappers.Child;
import com.asentinel.common.orm.mappers.PkColumn;
import com.asentinel.common.orm.mappers.Table;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import static com.asentinel.common.orm.ed.tree.EDTRPropertiesTestCase.isChildInList;
import static org.junit.Assert.*;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

public class EDTRMethodsTestCase {

	private static final Logger log = LoggerFactory.getLogger(EDTRMethodsTestCase.class);
	
	DefaultEntityDescriptorTreeRepository edtr = new DefaultEntityDescriptorTreeRepository();	

	@Test
	public void testAnnotatedMethods() {
		SqlBuilderFactory sbf = mock(SqlBuilderFactory.class);
		SqlQuery qEx = mock(SqlQuery.class);
		when(sbf.getSqlQuery()).thenReturn(qEx);
		edtr.setSqlBuilderFactory(sbf);
		
		Node<EntityDescriptor> node = edtr.getEntityDescriptorTree(Order2.class);
		log.debug("testAnnotatedMethods - tree: \n{}", node.toStringAsTree());

		node.traverse(node1 -> {
            SimpleEntityDescriptor ed = (SimpleEntityDescriptor) node1.getValue();

            // validate we set the SqlQuery in the row mapper so that blobs
            // cab be lazily loaded
            assertNotNull(((ConversionSupport) ed.getEntityRowMapper()).getQueryExecutor());

            if (ed.getTableName().equalsIgnoreCase("Orders")) {
                List<Node<EntityDescriptor>> children = node1.getChildren();
                assertEquals(2, children.size());
                assertTrue(isChildInList("DetailId", Detail2.class, children));
                assertTrue(isChildInList("DeliveryId", Delivery2.class, children));
            } else if (ed.getTableName().equalsIgnoreCase("Details")) {
                List<Node<EntityDescriptor>> children = node1.getChildren();
                assertEquals(1, children.size());
                assertTrue(isChildInList("OptionId", Option2.class, children));
            } else if (ed.getTableName().equalsIgnoreCase("Options")) {
                List<Node<EntityDescriptor>> children = node1.getChildren();
                assertEquals(0, children.size());
            } else if (ed.getTableName().equalsIgnoreCase("Deliveries")) {
                List<Node<EntityDescriptor>> children = node1.getChildren();
                assertEquals(0, children.size());
            } else {
                fail("Unexpected descriptor.");
            }
        });
	}


	@Test
	public void testAnnotatedMethodsWithCallback() {
		Node<EntityDescriptor> node = edtr.getEntityDescriptorTree(Order2.class,
                (node2, builder) -> {
                    if (node2.isRoot()) {
                        List<Cached> cachedList = Collections.emptyList();
                        CacheEntityDescriptor cached = CacheEntityDescriptor.forIntPk(Cached2.class, "CachedId", cachedList);
                        node2.addChild(new SimpleNode<>(cached));
                    } else if (builder.getName().toString().equalsIgnoreCase("DeliveryId")) {
                        List<Cached> cachedList = Collections.emptyList();
                        CacheEntityDescriptor cached = CacheEntityDescriptor.forIntPk(Cached2.class, "CachedId", cachedList);
                        node2.addChild(new SimpleNode<>(cached));
                    }
                    return true;
                }
        );
		log.debug("testAnnotatedMethods - tree: \n{}", node.toStringAsTree());

		node.traverse(node1 -> {
            EntityDescriptor ed = node1.getValue();
            if (ed.getName().toString().equalsIgnoreCase("OrderId")) {
                List<Node<EntityDescriptor>> children = node1.getChildren();
                assertEquals(3, children.size());
                assertTrue(isChildInList("DetailId", Detail2.class, children));
                assertTrue(isChildInList("DeliveryId", Delivery2.class, children));
                assertTrue(isChildInList("CachedId", Cached2.class, children));
            } else if (ed.getName().toString().equalsIgnoreCase("DetailId")) {
                List<Node<EntityDescriptor>> children = node1.getChildren();
                assertEquals(1, children.size());
                assertTrue(isChildInList("OptionId", Option2.class, children));
            } else if (ed.getName().toString().equalsIgnoreCase("OptionId")) {
                List<Node<EntityDescriptor>> children = node1.getChildren();
                assertEquals(0, children.size());
            } else if (ed.getName().toString().equalsIgnoreCase("DeliveryId")) {
                List<Node<EntityDescriptor>> children = node1.getChildren();
                assertEquals(1, children.size());
                assertTrue(isChildInList("CachedId", Cached2.class, children));
            } else if (ed.getName().toString().equalsIgnoreCase("CachedId")) {
                List<Node<EntityDescriptor>> children = node1.getChildren();
                assertEquals(0, children.size());
            } else {
                fail("Unexpected descriptor.");
            }
        });
	}
	
	
	@Test(expected = IllegalArgumentException.class)
	public void testNonQueryReadyRoot() {
		edtr.getEntityDescriptorTree(Order2.class,
                (node, builder) -> {
                    if (node.isRoot()) {
                        CacheEntityDescriptor cached = CacheEntityDescriptor.forIntPk(Cached2.class,
                                "CachedId", Collections.emptyList());
                        node.setValue(cached);
                    }
                    return true;
                }
        );
	}
	
	
	@Test
	public void testCachedBranchIsPruned() {
		Node<EntityDescriptor> root = edtr.getEntityDescriptorTree(Order2.class,
                (node, builder) -> {
                    if (builder.getEntityClass() == Detail2.class) {
                        CacheEntityDescriptor cached = CacheEntityDescriptor.forIntPk(Detail2.class,
                                "DetailId", Collections.emptyList());
                        node.setValue(cached);
                    }
                    return true;
                }
        );
		log.debug("testCachedBranchIsPruned - tree: \n{}", root.toStringAsTree());
		// count nodes
		List<Integer> counter = new ArrayList<>(1);
		counter.add(0);
		root.traverse(node -> {
            Integer c = counter.get(0) + 1;
            counter.set(0, c);
        });
		assertEquals(3, counter.get(0).intValue());
	}
}

@Table("Orders")
class Order2 implements Entity {

	@PkColumn("OrderId")
	int id;


	@Child(parentRelationType=RelationType.MANY_TO_ONE)
	void addDetail(Detail2 option) {

	}

	@Child(name="DeliveryId")
	void setDelivery(Delivery2 delivery) {

	}

	@Override
	public Object getEntityId() {
		return id;
	}

	@Override
	public void setEntityId(Object entityId) {
		id = (Integer) entityId;
	}

}

@Table("Details")
class Detail2 implements Entity {

	@PkColumn("DetailId")
	int id;


	@Child(parentRelationType=RelationType.MANY_TO_ONE)
	void addOption(Option2 option) {

	}


	@Override
	public Object getEntityId() {
		return id;
	}

	@Override
	public void setEntityId(Object entityId) {
		id = (Integer) entityId;	
	}

}

@Table("Options")
class Option2 implements Entity {

	@PkColumn("OptionId")
	int id;

	@Override
	public Object getEntityId() {
		return id;
	}

	@Override
	public void setEntityId(Object entityId) {
		id = (Integer) entityId;
	}
}

@Table("Deliveries")
class Delivery2 implements Entity {
	@PkColumn("DeliveryId")
	int id;

	@Override
	public Object getEntityId() {
		return id;
	}

	@Override
	public void setEntityId(Object entityId) {
		id = (Integer) entityId;
	}
}

@Table("Cached")
class Cached2 implements Entity {

	@PkColumn("CachedId")
	int id;

	@Override
	public Object getEntityId() {
		return id;
	}

	@Override
	public void setEntityId(Object entityId) {
		id = (Integer) entityId;
	}
}

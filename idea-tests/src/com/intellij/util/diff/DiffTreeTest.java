package com.intellij.util.diff;

import junit.framework.TestCase;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * @author max
 */
@SuppressWarnings({"HardCodedStringLiteral"})
public class DiffTreeTest extends TestCase {
  private static final Node[] EMPTY = new Node[0];

  private static class Node {
    private Node[] myChildren;
    int myId;


    public Node(final int id) {
      myChildren = EMPTY;
      myId = id;
    }

    public Node(final int id, Node... children) {
      myChildren = children;
      myId = id;
    }

    public int hashCode() {
      return myId + myChildren.length; // This is intentionally bad hashcode
    }

    public Node[] getChildren() {
      return myChildren;
    }

    public int getId() {
      return myId;
    }

    public String toString() {
      return String.valueOf(myId);
    }
  }

  private static class TreeStructure implements DiffTreeStructure<Node> {
    private Node myRoot;

    public TreeStructure(final Node root) {
      myRoot = root;
    }

    public Node prepareForGetChildren(final Node node) {
      return node;
    }

    public Node getRoot() {
      return myRoot;
    }

    public List<Node> getChildren(final Node node) {
      return Arrays.asList(node.getChildren());
    }
  }

  private static class NodeComparator implements ShallowNodeComparator<Node, Node> {
    public ThreeState deepEqual(final Node node, final Node node1) {
      return ThreeState.UNSURE;
    }

    public boolean typesEqual(final Node node, final Node node1) {
      return node.getId() == node1.getId();
    }

    public boolean hashcodesEqual(final Node node, final Node node1) {
      return node.hashCode() == node1.hashCode();
    }
  }

  public static class DiffBuilder implements DiffTreeChangeBuilder<Node, Node> {
    private List<String> myResults = new ArrayList<String>();

    public void nodeReplaced(final Node oldNode, final Node newNode) {
      myResults.add("REPLACED: " + oldNode + " to " + newNode);
    }

    public void nodeDeleted(final Node parent, final Node child) {
      myResults.add("DELETED from " + parent + ": " + child);
    }

    public void nodeInserted(final Node oldParent, final Node node, final int pos) {
      myResults.add("INSERTED to " + oldParent + ": " + node + " at " + pos);
    }

    public List<String> getEvents() {
      return myResults;
    }
  }

  public void testEmptyEqualRoots() throws Exception {
    Node r1 = new Node(0);
    Node r2 = new Node(0);
    final String expected = "";

    performTest(r1, r2, expected);
  }

  public void testSingleChildEqualRoots() throws Exception {
    Node r1 = new Node(0, new Node(1));
    Node r2 = new Node(0, new Node(1));
    final String expected = "";

    performTest(r1, r2, expected);
  }

  public void testTheOnlyChildRemoved() throws Exception {
    Node r1 = new Node(0, new Node(1));
    Node r2 = new Node(0);
    String expected = "DELETED from 0: 1";

    performTest(r1, r2, expected);
  }

  public void testTheOnlyChildAdded() throws Exception {
    Node r1 = new Node(0);
    Node r2 = new Node(0, new Node(1));
    String expected = "INSERTED to 0: 1 at 0";

    performTest(r1, r2, expected);

  }

  public void testTheOnlyChildReplaced() throws Exception {
    Node r1 = new Node(0, new Node(1));
    Node r2 = new Node(0, new Node(2));
    String expected = "REPLACED: 1 to 2";

    performTest(r1, r2, expected);
  }

  public void testInsertedIntoTheMiddle() throws Exception {
    Node r1 = new Node(0, new Node(1, new Node(21), new Node(23)));
    Node r2 = new Node(0, new Node(1, new Node(21), new Node(22), new Node(23)));
    String expected = "INSERTED to 1: 22 at 1";

    performTest(r1, r2, expected);
  }

  public void testInsertedFirst() throws Exception {
    Node r1 = new Node(0, new Node(1, new Node(22), new Node(23)));
    Node r2 = new Node(0, new Node(1, new Node(21), new Node(22), new Node(23)));
    String expected = "INSERTED to 1: 21 at 0";

    performTest(r1, r2, expected);
  }

  public void testInsertedLast() throws Exception {
    Node r1 = new Node(0, new Node(1, new Node(21), new Node(22)));
    Node r2 = new Node(0, new Node(1, new Node(21), new Node(22), new Node(23)));
    String expected = "INSERTED to 1: 23 at 2";

    performTest(r1, r2, expected);
  }

  public void testInsertedTwoLast() throws Exception {
    Node r1 = new Node(0, new Node(1, new Node(21), new Node(22)));
    Node r2 = new Node(0, new Node(1, new Node(21), new Node(22), new Node(23), new Node(24)));

    performTest(r1, r2, "INSERTED to 1: 23 at 2", "INSERTED to 1: 24 at 3");
  }

  public void testSubtreeAppears() throws Exception {
    Node r1 = new Node(0, new Node(1, new Node(21), new Node(22), new Node(23)));
    Node r2 = new Node(0, new Node(1, new Node(21), new Node(22, new Node(221)), new Node(23)));

    performTest(r1, r2, "INSERTED to 22: 221 at 0");
  }

  public void testSubtreeChanges() throws Exception {
    Node r1 = new Node(0, new Node(1, new Node(21), new Node(22, new Node(221)), new Node(23)));
    Node r2 = new Node(0, new Node(1, new Node(21), new Node(250, new Node(222)), new Node(23)));

    performTest(r1, r2, "REPLACED: 22 to 250");
  }

  private static void performTest(final Node r1, final Node r2, final String... expected) {
    final DiffBuilder result = new DiffBuilder();
    DiffTree.diff(new TreeStructure(r1), new TreeStructure(r2), new NodeComparator(), result);

    final List<String> expectedList = Arrays.asList(expected);
    final List<String> actual = result.getEvents();
    if (expectedList.size() > 0 && actual.size() > 0) {
      assertEquals(expectedList, actual);
    }
  }

}

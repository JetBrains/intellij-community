// IDEA-286285
class Node {
  Node next;

  public void test(Node n1, Node n2) {
    n1.next = n2;
    n1 = next;
    n1.next = null;
  }
}

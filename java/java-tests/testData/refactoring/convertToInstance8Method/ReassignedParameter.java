class X {
  static Node <caret>skipForward2(Node node, Predicate<Node> skipWhile) {
    while (skipWhile.test(node)) {
      Node next = node.nextNode();
      if (next == null) break;
      node = next;
    }
    return node;
  }

  class Node {

    Node nextNode() {
      return null;
    }
  }
}
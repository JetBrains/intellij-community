class Node {
  String getType() { return ""; }

  String foo(Node left, Node right) {
    String leftType = left.getType().substring(1);
    <selection>String rightType = right.getType().substring(1);</selection>

    String type = "A".substring(1);
    return leftType + rightType + type;
  }
}
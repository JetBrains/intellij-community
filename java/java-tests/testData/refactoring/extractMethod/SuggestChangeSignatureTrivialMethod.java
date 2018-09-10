class Node {
    String getType() { return ""; }

    String foo(Node left, Node right) {
        String leftType = left.getType();
        <selection>String rightType = (right.getType());</selection>

        String type = "A";
        return leftType + rightType + type;
    }
}
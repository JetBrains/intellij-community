class Node {
    String getType() { return ""; }

    String foo(Node left, Node right) {
        String leftType = newMethod(left);
        String rightType = newMethod(right);

        String type = "A";
        return leftType + rightType + type;
    }

    private String newMethod(Node right) {
        return right.getType();
    }
}
import org.jetbrains.annotations.NotNull;

class Node {
  String getType() { return ""; }

  String foo(Node left, Node right) {
      String leftType = newMethod(left.getType());
      String rightType = newMethod(right.getType());

      String type = newMethod("A");
      return leftType + rightType + type;
  }

    @NotNull
    private String newMethod(String type2) {
        return type2.substring(1);
    }
}
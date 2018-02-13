
import java.util.function.Function;

class Test {
  static class Node { }
  static class ValueNode extends Node {}

  static void m(ValueNode v, Node n) {
    pathToCustomNode((Node)v, current -> n);
  }

  public static <T> void pathToCustomNode(T node, Function<T, T> getParent) { }

}
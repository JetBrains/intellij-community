// "Import static constant 'foo.B.TYPE_NODE'" "true"
package foo;

class Static {
  private enum NodeType {
    TYPE_NODE
  }

  boolean is(Object object) {
    return object == TYPE<caret>_NODE;
  }


}
class B {
  public static Object TYPE_NODE = null;
}
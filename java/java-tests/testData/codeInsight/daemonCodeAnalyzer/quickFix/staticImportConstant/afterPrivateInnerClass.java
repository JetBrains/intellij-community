// "Import static constant 'foo.B.TYPE_NODE'" "true"
package foo;

import static foo.B.TYPE_NODE;

class Static {
  private enum NodeType {
    TYPE_NODE
  }

  boolean is(Object object) {
    return object == TYPE_NODE;
  }


}
class B {
  public static Object TYPE_NODE = null;
}
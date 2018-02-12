import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

class ArrayInitializerLength {
  void testDeclaration() {
    String[] arr = {"foo"};
    if(<warning descr="Condition 'arr.length == 2' is always 'false'">arr.length == 2</warning>) {
      System.out.println("oops");
    }
  }

  void testNewExpression() {
    String[] arr = new String[]{"foo"};
    if(<warning descr="Condition 'arr.length == 2' is always 'false'">arr.length == 2</warning>) {
      System.out.println("oops");
    }
  }

  void testDimension() {
    int[] arr = new int[3];
    if(<warning descr="Condition 'arr.length == 1' is always 'false'">arr.length == 1</warning>) {
      System.out.println("oops");
    }
  }

  void testIterate() {
    int[] arr = new int[0];
    for (int i : <warning descr="Array 'arr' is always empty">arr</warning>) {
      System.out.println("never");
    }
  }

  void testConditional() {
    int[] arr = Math.random() > 0.5 ? new int[2] : new int[4];
    if(<warning descr="Condition 'arr.length == 3' is always 'false'">arr.length == 3</warning>) {
      System.out.println("never");
    }
    if(arr.length == 2) {
      System.out.println("possible");
    }
  }

  void testMultiDimensional() {
    int[][][] arr = (new int[1][2][3]);
    if(<warning descr="Condition 'arr.length == 1' is always 'true'">arr.length == 1</warning>) {
      System.out.println("ok");
    }
    if(<warning descr="Condition 'arr.length == 2' is always 'false'">arr.length == 2</warning>) {
      System.out.println("not ok");
    }
    if(<warning descr="Condition 'arr.length == 3' is always 'false'">arr.length == 3</warning>) {
      System.out.println("not ok");
    }
  }

  void testExecutionOrder(Object obj) {
    if(obj instanceof String) {
      // should not warn about possible CCE
      obj = new String[] {(String)obj};
    }
    System.out.println(obj);
  }
}
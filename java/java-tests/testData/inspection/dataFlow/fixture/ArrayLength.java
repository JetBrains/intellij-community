import java.util.Arrays;

public final class ArrayLength {
  void testForSimple2(int[] arr, int[][] arr2) {
    boolean b = arr2[0].length == arr.length;
    for(int i=0; i<arr.length; i++) {
      if(b) {
        System.out.println(1);
      } else {
        System.out.println(2);
      }
      if(<warning descr="Condition 'i == arr.length' is always 'false'">i == arr.length</warning>) {
        System.out.println("impossible");
      }
    }
  }

  void testForSimple(int[] arr) {
    for(int i=0; i<arr.length; i++) {
      if(<warning descr="Condition 'i == arr.length' is always 'false'">i == arr.length</warning>) {
        System.out.println("impossible");
      }
    }
  }

  void testEquality(int[] x) {
    int len = x.length;
    for(int i=0; i<100; i++) {
      if (i == len) {
        x[<warning descr="Array index is out of bounds">i</warning>] = 10;
      }
    }
  }

  int[] testFor(int length) {
    int[] x = new int[length];
    for(int i=0; i<=length; i++) {
      // TODO: warn
      x[i] = 1;
    }
    return x;
  }

  public static void testArray2(Object[] x, Object[] y, int a) {
    if(<warning descr="Condition 'x[a] == null && a == x.length' is always 'false'">x[a] == null && <warning descr="Condition 'a == x.length' is always 'false' when reached">a == x.length</warning></warning>) {
      System.out.println("Impossible");
    }
    y[0] = 1;
    if(<warning descr="Condition 'y.length > 0' is always 'true'">y.length > 0</warning>) {
      System.out.println("Always");
    }
  }

  public static void testArray(int[] x, int a) {
    x[<warning descr="Array index is out of bounds">-1</warning>] = -2;
    x[<warning descr="Array index is out of bounds">x.length</warning>] = -1;
    x[a] = 6;
    if(<warning descr="Condition 'a < 0' is always 'false'">a < 0</warning>) {
      System.out.println("never");
    }
  }

  public static void testArrayMethods(int[] x) {
    Arrays.fill(x, 0, 10, -1);
    if(<warning descr="Condition 'x.length < 10' is always 'false'">x.length < 10</warning>) {
      System.out.println("Impossible");
    }
    Arrays.fill(x, -1);
    Arrays.<warning descr="The call to 'fill' always fails as index is out of bounds">fill</warning>(x, -1, -1, -1);
  }

  static final String[] ARR1 = {};
  static final String[] ARR2 = new String[10];
  static final String[] ARR3 = new String[] {"foo", "bar", "baz"};

  void testFieldInitializers() {
    if(<warning descr="Condition 'ARR1.length == 0' is always 'true'">ARR1.length == 0</warning>) System.out.println("yes");
    if(<warning descr="Condition 'ARR2.length == 10' is always 'true'">ARR2.length == 10</warning>) System.out.println("yes");
    if(<warning descr="Condition 'ARR3.length == 3' is always 'true'">ARR3.length == 3</warning>) System.out.println("yes");
  }
}
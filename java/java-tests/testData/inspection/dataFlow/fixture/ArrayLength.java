import java.util.Arrays;

public final class ArrayLength {
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
    Arrays.<warning descr="The call to 'fill' always fails, according to its method contracts">fill</warning>(x, -1, -1, -1);
  }
}
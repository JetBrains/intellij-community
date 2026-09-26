import java.util.Arrays;
// IDEA-265608
public class OverwrittenKeyArray {
  public static final String HELLO_BUG = "Hello, bug!";
  public static void main(String... args) {
    int[] a = new int[1];
    a[<warning descr="Overwritten array element">0</warning>] = 314;
    a[<warning descr="Overwritten array element">0</warning>] = 314; // (1)
    System.out.println("a = " + Arrays.toString(a));
    String[] b = new String[1];
    b[<warning descr="Overwritten array element">0</warning>] = HELLO_BUG;
    b[<warning descr="Overwritten array element">0</warning>] = HELLO_BUG; // (2)
    System.out.println("b = " + Arrays.toString(b));
    int[] c = new int[1];
    setFirstItem(c, 159);
    System.out.println("c = " + Arrays.toString(c));
    int[] d = new int[1];
    setItem(d, 0, 265);
    System.out.println("d = " + Arrays.toString(d));
  }
  private static void setFirstItem(int[] array, int value) {
    int index = 0;
    array[<warning descr="Overwritten array element">index</warning>] = value;
    array[<warning descr="Overwritten array element">index</warning>] = value; // (3)
  }
  private static void setItem(int[] array, int index, int value) {
    array[<warning descr="Overwritten array element">index</warning>] = value;
    array[<warning descr="Overwritten array element">index</warning>] = value; // (4)
  }
}
// "Remove 'for' statement" "true"

public class Test {

  public static int[] init(int[] arr, boolean b) {
    arr = new int[10];
    if (b) {
      arr = new int[20];
    }
      return arr;
  }
}
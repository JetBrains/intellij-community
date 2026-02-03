// "Replace loop with 'Arrays.fill()' method call" "true"

public class Test {

  public static int[] init(int[] arr, boolean b) {
    if (b) {
      arr = new int[10];
    }
    for (<caret>int i = 0; i < arr.length; i++) {
      arr[i] = 0;
    }
    return arr;
  }
}
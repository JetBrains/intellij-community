// "Replace loop with 'Arrays.fill()' method call" "true"

public class Test {

  public static int[] init(boolean b) {
    int[] arr = new int[10];
    if (b) {
      arr = new int[]{1, 2, 3, 4, 5};
    }
    for (<caret>int i = 0; i < arr.length; i++) {
      arr[i] = 0;
    }
    return arr;
  }
}
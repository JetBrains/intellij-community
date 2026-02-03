// "Remove 'for' statement" "true"

public class Test {

  public static int[] init(int n, boolean b) {
    int[] data = new int[n];
    for (<caret>int j = 0; j < data.length; j++) {
      data[j] = 0;
    }
    data[n - 1] = 6;
    return data;
  }
}
// "Remove 'for' statement" "true"

class Test {

  public static int[] init(int n, boolean b) {
    int[] data = new int[n];
    for (<caret>int j = 0; j < n; j++) {
      data[j] = 0;
    }
    data[n - 1] = 6;
    return data;
  }
}
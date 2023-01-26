// "Remove 'for' statement" "false"

class Test {

  public static int[] init(int n, boolean b) {
    int[] data = new int[n];
    data = new int[n + 1];
    for (<caret>int j = 0; j < n; j++) {
      data[j] = 0;
    }
    return data;
  }
}
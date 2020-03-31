// "Replace loop with 'Arrays.fill()' method call" "true"

class Test {
  private void test2() {
    int[][] arr = new int[10][];
    for (<caret>int i = 0; i < arr.length; i++) {
      arr[i] = new int[0];
    }
  }
}
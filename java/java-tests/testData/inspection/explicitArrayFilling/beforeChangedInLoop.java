// "Replace loop with 'Arrays.setAll()' method call" "true"

class Test {

  void fill2DArray() {
    final double[][] arr = new double[2][];
    for (<caret>int i = 0; i < arr.length; i++) {
      arr[i] = new double[1];
    }
  }
}
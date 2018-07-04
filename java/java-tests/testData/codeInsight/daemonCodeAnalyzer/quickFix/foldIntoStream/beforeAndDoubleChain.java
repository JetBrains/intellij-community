// "Fold expression into Stream chain" "true"
class Test {
  boolean foo(double[] arr) {
    return arr[1] >= 5 && arr[3] >= 5 && arr[7] >= 5 && arr[9] <caret>>= 5;
  }
}
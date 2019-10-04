// "Replace loop with 'Arrays.fill()' method call" "true"

class Test {

  private void testLambdas() {
    Supplier[] arr = new Supplier[10];
    for (<caret>int i = 0; i < arr.length; i++) {
      arr[i] = () -> new int[10];
    }
  }

}
// "Replace loop with 'Arrays.setAll()' method call" "true"

class Test {

  void test(boolean choice) {
    Object[] arr = new Object[10];
    for (<caret>int i = 0; i < arr.length; i++) {
      arr[i] = (choice ? "foo" : new Object());
    }
  }

}
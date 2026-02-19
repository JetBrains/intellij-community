// "Add 'finally' block" "true-preview"
class X {
  void test() {
    try {
      System.out.println();
    }<caret> // todo
  }
}
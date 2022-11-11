// "Remove 'if' statement" "true-preview"
class X {
  void test() {
    Object obj = "Hello from pattern matching";
    if (obj instanceof<caret> Integer i) {
      System.out.println(i);
    }
  }
}
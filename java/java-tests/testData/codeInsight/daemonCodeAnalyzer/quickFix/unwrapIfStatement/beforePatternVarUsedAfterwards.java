// "Remove 'if' statement extracting side effects" "true-preview"
class X {
  void test() {
    Object obj = "Hello from pattern matching";
    if (!(obj instanceof <caret>String s)) return;
    System.out.println(s);
  }
}
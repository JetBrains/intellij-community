// "Remove 'if' statement extracting side effects" "true-preview"
class X {
  void test() {
    Object obj = "Hello from pattern matching";
    if (!(getString() instanceof <caret>String s)) return;
    System.out.println(s);
    System.out.println(s);
  }
  
  native @org.jetbrains.annotations.NotNull String getString();
}
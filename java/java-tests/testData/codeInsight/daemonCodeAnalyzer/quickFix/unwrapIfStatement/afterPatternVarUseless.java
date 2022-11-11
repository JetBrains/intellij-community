// "Remove 'if' statement extracting side effects" "true-preview"
class X {
  void test() {
    Object obj = "Hello from pattern matching";
      String s = getString();
      System.out.println(s);
    System.out.println(s);
  }
  
  native @org.jetbrains.annotations.NotNull String getString();
}
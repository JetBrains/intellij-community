// "Remove 'if' statement extracting side effects" "true"
class X {
  void test() {
    Object obj = "Hello from pattern matching";
      String s = (String) obj;
      System.out.println(s);
  }
}
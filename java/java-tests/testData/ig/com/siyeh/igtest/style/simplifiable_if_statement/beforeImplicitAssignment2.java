// "Replace 'if else' with '?:'" "INFORMATION"
class X {
  void test(int f) {
    String s = "none";
    System.out.println(s);
    s = "true";
    <caret>if (f > 0) s = "false";
  }
}
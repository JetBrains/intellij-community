// "Join declaration and assignment" "false"
class C {
  void test() {
    String t;
    String <caret>s;
    t = "foo";
  }
}
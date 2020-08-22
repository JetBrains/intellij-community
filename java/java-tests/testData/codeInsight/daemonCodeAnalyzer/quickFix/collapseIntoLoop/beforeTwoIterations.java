// "Collapse into loop" "true"
class X {
  void test() {
    <caret>System.out.println(12);
    System.out.println(17);
  }
}
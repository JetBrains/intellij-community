// "Collapse into loop" "true-preview"
class X {
  void test() {
    <caret>System.out.println(12);
    System.out.println(17);
    System.out.println(22);
  }
}
// "Replace with 'y'" "true-preview"
class X {
  void test(int x, int y) {
    if (x > y) return;
    System.out.println(Math.<caret>max(x, y));
  }
}
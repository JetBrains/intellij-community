// "Compute constant value of 'i'" "true-preview"
class Test {
  void test(int i) {
    if (i != 10) return;
    System.out.println(<caret>i);
  }
}
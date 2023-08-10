// "Compute constant value of 'Math.sqrt(...) + 10'" "true-preview"
class Test {
  void test() {
    double res = Math.<caret>sqrt(2) + 10;
  }
}
// "Compute constant value of 'x'" "true-preview"
class Test {
  enum X {A, B}
  
  void test() {
    X x = X.A;

    System.out.println(<caret>x);
  }
}
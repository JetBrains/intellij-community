class Test {
  void nestedParenthesisTest(int a, int b, int c) {
    int d = c * /*1*/(/*2*/(/*3*/a <caret>+ b));
    int e = c * /*1*/(/*2*/(a) <caret>+ b);
    int f = a - /*1*/(/*2*/(/*3*/b <caret>-/*4*/c));
    int g = a - /*1*/(/*2*/(/*3*/(b) <caret>- c));
  }
}
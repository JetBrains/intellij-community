// "Replace with 'Math.max()' call" "true"
class Test {
  void test(int a, int b) {
    int c/*0*/ = (((a) <caret> > /*1*/(b)) ? /*2*/(a) : /*3*/(b));
  }
}
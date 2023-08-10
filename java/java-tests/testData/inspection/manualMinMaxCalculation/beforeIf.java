// "Replace with 'Math.min()' call" "true"
class Test {

  void test(int a, int b) {
    int c;
    if<caret>/*0*/(((a) /*1*/< (b))) {
      c = (a)/*2*/;
    }
    else c/*3*/ = (b);
  }
}
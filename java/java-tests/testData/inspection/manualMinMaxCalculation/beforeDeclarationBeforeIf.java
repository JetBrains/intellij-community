// "Replace with 'Math.min()' call" "true"
class Test {

  void test(int maxDrain) {
    int /*0*/drained = /*1*/maxDrain;
    if<caret> /*2*/(10/*3*/ < maxDrain) {
      drained/*4*/ = 10/*5*/;
    }/*6*/
  }
}
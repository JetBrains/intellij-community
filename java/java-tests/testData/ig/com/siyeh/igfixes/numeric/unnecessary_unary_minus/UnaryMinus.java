class Test {
  void test(int a, int b, boolean f) {
    int i = - - - a;
    i = - -4;
    i = -a;
    i = a + <caret>-8;
    boolean res = f && ((1 + /*a*/ - /*b*/ getResult() + /*c*/ - /*d*/ 2 - - /*e*/ getResult()) > 0);
    String s = "Hello World" + -1 + "asdf";
    byte b = 2;
    test(- - b);
    char c = 3;
    test(- -c);
  }

  void test() {
    byte i = 1;
    test(+i);
  }

  void test(short s) {
  }

  void test(int i) {
  }

  int getResult() {return 0;}
}
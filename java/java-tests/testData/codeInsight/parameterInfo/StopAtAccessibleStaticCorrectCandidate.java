
class A {
  static void foo(int x){}
  class Inner {
    void  foo(){}

    void test() {
      foo(<caret>1);
    }
  }
}
class Test {
  void bar(A a){
    a.foo();
  }
  class A {
    void f<caret>oo(){int i = 0;}
  }
}
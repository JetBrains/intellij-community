class A {
  void foo(){
    //do smth in A
  }
}

class B {
  void <caret>bar() {
    super.foo();
  }

  void test(){
    bar();
  }

  void foo() {
    //do smth
  }
}
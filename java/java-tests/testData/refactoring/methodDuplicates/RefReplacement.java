class Test {
  static void <caret>main(Test t){
    if (true) t.foo();
  }

  void foo(){}
}

class Test1 {
  void bar(Test t) {
    if (true) {t.foo();}
  }
}
class B extends A {
  void n<caret>(C c){
    m();
  }
}

class A {
  void m(){}
}

class C {}
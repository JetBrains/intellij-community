class A<T> {
  T bar(){}
}

class B extends A<S<caret>tring>{
  void barInner(String s) {
  }

  void foo() {
    barInner(bar());
  }
}
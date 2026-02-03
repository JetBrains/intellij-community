class Test {
  void foo() {
     <caret>getObject().notify();
  }

  Object getObject() {return null;}
}
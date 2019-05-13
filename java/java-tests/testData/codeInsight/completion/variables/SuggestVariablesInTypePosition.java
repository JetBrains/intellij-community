class A {
  int myField;
  int myField2;
  A() {
    myF<caret> initField();
  }

  int initField(){return 2;}
}
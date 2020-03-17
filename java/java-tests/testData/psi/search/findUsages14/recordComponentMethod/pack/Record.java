package pack;

record MyRecord (String s) {
  void foo() {
    String s = s();
  }
}

class A {
  void bar() {
    MyRecord asd = new MyRecord("asd");
    asd.s();
  }
}
class A {
  void methodA() {};

  final void methodB() {};
}

class B extends A {
  private A a;


  <caret>

}
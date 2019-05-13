class A {
  void methodA() {};

  final void methodB() {};
}

class B {
  private A a;


    public void methodA() {
        a.methodA();
    }
}
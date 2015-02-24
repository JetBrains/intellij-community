class A {
  void methodA() {};

  final void methodB() {};
}

class B extends A {
  private A a;


    @Override
    public void methodA() {
        a.methodA();
    }
}
class Foo {
  public int f() {
    return 1;
  }
}
class D {
  public void run() {
    Foo foo = new Foo()
    foo.f() + foo.f();
    foo.f()+foo.f();
    int ff1 = foo.f() +1;
    int ff2 = foo.f() +2;
  }
}
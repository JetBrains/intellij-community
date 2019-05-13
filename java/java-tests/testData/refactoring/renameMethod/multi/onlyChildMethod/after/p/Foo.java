package p;
class Bar {
  public void foo() {}
}

class Foo extends Bar {
  public void bar() {
    super.foo();
  }
}

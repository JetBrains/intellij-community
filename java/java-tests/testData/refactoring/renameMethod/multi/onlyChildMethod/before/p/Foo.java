package p;
class Bar {
  public void foo() {}
}

class Foo extends Bar {
  @Override
  public void foo() {
    super.foo();
  }
}

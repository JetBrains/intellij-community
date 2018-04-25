class FooImpl extends Foo {
  @Override
  public String toString() {
    return "FooImpl instance";
  }
}

class Foo {
  @Override
  public String toString() {
    return "Foo instance";
  }
}
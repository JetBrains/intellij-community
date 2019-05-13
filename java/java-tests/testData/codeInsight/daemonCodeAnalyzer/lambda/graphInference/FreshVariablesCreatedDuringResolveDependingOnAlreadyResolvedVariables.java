class Test {

  class X<F extends Foo, B extends Bar<F, B>> {
  }
  class Foo {
  }
  class Bar<A, B> {
  }

  private <F extends Foo, B extends Bar<F,  B>> X<F, B> foo() {
    return null;
  }

  {
    X x = foo();
  }
}

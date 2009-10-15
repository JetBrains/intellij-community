import pkg.Foo;

class Bar {

  public void foo() {
    new Foo(hashCode())<caret> {}
  }

}
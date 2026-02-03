interface I {
  void setFoo(int o);
}

interface Foo {
  void setFoo(int foo);
}

class Bar implements Foo, I {
  int fo<caret>o;

  @Override
  public void setFoo(int foo) {
    this.foo = foo;
  }
}
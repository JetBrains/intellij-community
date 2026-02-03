import foo.*;

class Bar extends Foo {
  protected void foo();
}

class Goo extends Foo {
  {
    foo();
  }
}
class Foo {
  String s = <warning descr="Variable 's' initializer '\"foo\"' is redundant">"foo"</warning>;

  Foo(String _s) {
    s = _s;
  }

  Foo() {
    this("foo");
  }

  public static void main(String[] args) {
    new Foo();
  }
}
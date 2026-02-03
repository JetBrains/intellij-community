import static Foo.foo;

class Foo {
  public static <T> T foo ()

}

class Bar {
  void f(String s) {
  }

  {
    f(foo<caret>x)
  }
}
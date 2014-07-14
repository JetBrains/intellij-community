class Test {

  static class Foo<X> {
    X m() { return null;}
  }

  interface I {
    Foo<Object> _i(Foo<String> fs);
  }

  static void foo(I i) { }
  {
    foo(<error descr="Bad return type in method reference: cannot convert java.lang.String to Test.Foo<java.lang.Object>">Foo::m</error>);
  }
}

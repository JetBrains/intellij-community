class Test {

  void f(String strings) {
    extract("a", "b", "c", "d");
  }

  private static void extract(final String from, final String to, final String... extensions)  {

    new Foo(<selection>extensions</selection>);
  }

  private static class Foo {
    public Foo(String[] extensions) {
    }
  }
}

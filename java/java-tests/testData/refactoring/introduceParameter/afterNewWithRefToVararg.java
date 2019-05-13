class Test {

  void f(String strings) {
      final String[] strings1 = new String[]{"c", "d"};
      final Foo foo = new Foo(strings1);
      extract("a", "b", foo);
  }

  private static void extract(final String from, final String to, Foo anObject)  {

  }

  private static class Foo {
    public Foo(String[] extensions) {
    }
  }
}

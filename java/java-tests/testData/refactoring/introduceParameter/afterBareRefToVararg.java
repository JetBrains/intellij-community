class Test {

  void f(String strings) {
      final String[] strings1 = new String[]{"c", "d"};
      extract("a", "b", strings1);
  }

  private static void extract(final String from, final String to, String[] anObject)  {

    new Foo(anObject);
  }

  private static class Foo {
    public Foo(String[] extensions) {
    }
  }
}

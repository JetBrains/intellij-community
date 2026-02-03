import static Foo.assertNotNull;

class Foo {
  static void assertNotNull() {}
}

class Bar {
  {
    asnn<caret> lists.get(0).size() > 0);
  }
}
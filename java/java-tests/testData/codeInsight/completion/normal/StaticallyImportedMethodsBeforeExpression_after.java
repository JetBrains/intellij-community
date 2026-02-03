import static Foo.assertNotNull;

class Foo {
  static void assertNotNull() {}
}

class Bar {
  {
    assertNotNull();<caret> lists.get(0).size() > 0);
  }
}
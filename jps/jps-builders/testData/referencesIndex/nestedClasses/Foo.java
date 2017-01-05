import java.util.Collections;

class Foo {

  void m() {
    Collections.emptyList();
  }

  static class Bar {

    void m() {
      Collections.emptyMap();
    }

  }

}

class Boo {

  static class Baz {

    void m() {
      Collections.singleton(null);
    }

  }

  void m() {
    Collections.emptySet();
  }

}
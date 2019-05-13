public class Foo {
  void m(Object o) {
    if (o instanceof Boolean) {
      o.castvar<caret>
    }
  }
}
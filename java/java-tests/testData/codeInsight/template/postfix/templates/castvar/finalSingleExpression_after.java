public class Foo {
  void m(Object o) {
    if (o instanceof Boolean) {
        final Boolean b = (Boolean) o;
    }
  }
}
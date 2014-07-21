import java.util.List;

// "Cast parameter to 'java.util.List'" "true"
class Test {
  void m(Object o) {
    foo((List) o);
  }

  private void foo(final java.util.List o) {}
}

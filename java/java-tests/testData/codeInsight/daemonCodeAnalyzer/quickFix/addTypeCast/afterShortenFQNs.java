import java.util.List;

// "Cast argument to 'List'" "true-preview"
class Test {
  void m(Object o) {
    foo((List) o);
  }

  private void foo(final java.util.List o) {}
}

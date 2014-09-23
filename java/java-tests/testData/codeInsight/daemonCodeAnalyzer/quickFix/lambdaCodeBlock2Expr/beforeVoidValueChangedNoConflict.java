// "Replace with expression lambda" "true"
class Test {
  {
    a(() -> {
      ret<caret>urn new Object(){};
    });
  }

  void a(Supplier<Object> s) {}
  void a(AI<Object> s) {}

  interface AI<K> {
    void m();
  }
  interface Supplier<T> {
    T get();
  }
}
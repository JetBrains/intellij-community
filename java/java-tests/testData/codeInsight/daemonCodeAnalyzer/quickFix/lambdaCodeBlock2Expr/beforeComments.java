// "Replace with expression lambda" "false"
class Test {
  {
    a(() -> {
      //my comment here
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
interface Test {

  final class Inner {

    void func() {
      of("");
      of();
      of("", "");
    }

  }

  static void of(String... lists) { }

}

interface Entry<T> { }
interface ClassA<T> {
  static <T> void f(Iterable<T> values) {
  }
}
interface ClassB<T> extends ClassA<Entry<T>> {
  static <T> void f(Iterable<? extends Entry<? extends T>> values) {}
  
  static void m(Iterable<Entry<String>> x) {
    f(x);
  }
}
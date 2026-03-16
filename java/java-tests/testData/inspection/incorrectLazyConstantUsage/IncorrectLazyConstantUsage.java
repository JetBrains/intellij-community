class Main {
  static final LazyConstant<String> a = LazyConstant.of(() -> "Hello");
  final LazyConstant<String> b;
  LazyConstant<String> <warning descr="'LazyConstant' field should be 'final'">f</warning> = LazyConstant.of(() -> "Bye");

  static void main() {
    LazyConstant<String> c = LazyConstant.of(() -> "Hello");
  }

  Main() {
    b = LazyConstant.of(() -> "World");
    LazyConstant<String> d = LazyConstant.of(() -> "Hello");
    LazyConstant<String> e = returnLazyConstant(d);
  }

  LazyConstant<String> returnLazyConstant(LazyConstant<String> lc) {
    return LazyConstant.of(() -> lc.<error descr="'get()' is not public in 'java.lang.LazyConstant'. Cannot be accessed from outside package">get</error>());
  }
}

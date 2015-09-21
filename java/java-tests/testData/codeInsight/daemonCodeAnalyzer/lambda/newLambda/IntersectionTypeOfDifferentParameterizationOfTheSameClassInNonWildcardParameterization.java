interface A<T> {
  void method(B<? extends A<? super T>> arg);
}

interface B<T extends A<?>> {
  void method();
}

class Test {
  public static void test(A<?> a) {
    a.method(() -> {});
  }
}
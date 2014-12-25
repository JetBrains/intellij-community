
interface A<T> {
  void method();
}

interface B<T extends A<?>> {
  void method(T arg);
}

interface C {
  void method(B<? extends A<String>> arg);
}

class Test {
  public static void test(C c) {
    c.method(arg -> arg.method( ));
  }
}
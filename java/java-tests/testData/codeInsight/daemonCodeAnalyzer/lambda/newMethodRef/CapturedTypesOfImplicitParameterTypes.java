interface A<T> {
}

interface B<BT> {
  void method(BT arg);
}

class Test {
  public static void test() {
    method1(Test::<error descr="Cannot resolve method 'method2'">method2</error>);
  }

  static <M> void method1(B<A<? super M>> arg) { }

  static void method2(A<? super String> arg) { }
}
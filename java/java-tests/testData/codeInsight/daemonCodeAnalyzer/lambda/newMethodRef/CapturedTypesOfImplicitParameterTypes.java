interface A<T> {
}

interface B<BT> {
  void method(BT arg);
}

class Test {
  public static void test() {
    method1(Test::<error descr="Invalid method reference: A<capture of ? super M> cannot be converted to A<? super String>">method2</error>);
  }

  static <M> void method1(B<A<? super M>> arg) { }

  static void method2(A<? super String> arg) { }
}
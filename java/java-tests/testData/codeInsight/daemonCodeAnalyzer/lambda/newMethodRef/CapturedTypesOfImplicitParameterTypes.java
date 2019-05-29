interface A<T> {
}

interface B<BT> {
  void method(BT arg);
}

class Test {
  public static void test() {
    method1<error descr="'method1(B<A<? super M>>)' in 'Test' cannot be applied to '(<method reference>)'">(Test::method2)</error>;
  }

  static <M> void method1(B<A<? super M>> arg) { }

  static void method2(A<? super String> arg) { }
}
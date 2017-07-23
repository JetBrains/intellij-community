class C {
  static class MyThrowable<T> extends <error descr="Generic class may not extend 'java.lang.Throwable'">Throwable</error> { }
  void test() throws <error descr="Generic class may not extend 'java.lang.Throwable'">MyThrowable<Integer></error> { }
}
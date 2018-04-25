class Test {
  Test() {
    foo(<error descr="Unhandled exception: java.lang.Exception">this::fs</error>);
    foo((s) -> <error descr="Unhandled exception: java.lang.Exception">fs</error>(s));
  }

  void foo(I<String, String> iss) {}
  String fs(String s) throws Exception { throw new Exception(); }
}

interface I<T, R> {
  R f(T t);
}
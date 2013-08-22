interface I<T extends Exception> {
  void m() throws T;
}

class C {
  void x(I<?> i) {
    <error descr="Unhandled exception: java.lang.Exception">i.m();</error>
  }

  void y(I<? extends Exception> i) {
    <error descr="Unhandled exception: java.lang.Exception">i.m();</error>
  }
}
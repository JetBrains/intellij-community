interface I<T extends Exception> {
  void m() throws T;
}

class C {
  void x(I<?> i) {
    i.<error descr="Unhandled exception: java.lang.Exception">m();</error>
  }

  void y(I<? extends Exception> i) {
    i.<error descr="Unhandled exception: java.lang.Exception">m();</error>
  }
}
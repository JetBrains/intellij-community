class GenericThrow<T extends Throwable> {
  T t;
  void x() throws T {
    throw t;
  }
}
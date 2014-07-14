class Sample {
  static <T> T foo(T t) { return null; }

  static {
    long l11 = foo(1 );
  }
}

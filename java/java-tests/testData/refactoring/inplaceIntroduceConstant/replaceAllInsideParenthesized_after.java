class Test {

  Test foo(long l) {
    return this;
  }

    public static final long LONG = 5L;

    {
    Test t = new Test()
      .foo(-LONG)
      .foo(7L)
      .foo(-LONG);
  }
}

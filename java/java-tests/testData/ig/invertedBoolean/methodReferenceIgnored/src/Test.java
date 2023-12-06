class Test {
  {
    I ii = this::isFoo;
    if (!isFoo());
  }

  boolean isFoo() {
    return true;
  }

  interface I {
    boolean i();
  }
}
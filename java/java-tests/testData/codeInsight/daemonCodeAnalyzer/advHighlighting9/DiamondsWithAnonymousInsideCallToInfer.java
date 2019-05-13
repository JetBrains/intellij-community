class Foo {
  public void foo() {
    String s = foo(new I<>() {
        public String m() {
          return null;
        }
      });
  }

  static <T> T foo(I<T> action) {
    return null;
  }

  interface I<T> {
    T m();
  }

}

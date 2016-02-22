class A {
  static class B<T> {
    int foo(T x) {return 0;}
  }
  public static void main(String[] args) {
    B<? extends CharSequence> q = new B<>();

    Func x = q::<error descr="Invalid method reference: CharSequence cannot be converted to capture of ? extends CharSequence">foo</error>;
    x.invoke("");
  }

  interface Func {
    int invoke(CharSequence x);
  }
}
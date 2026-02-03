interface I<T>{
  void foo(Function<T, T> f);
}

class C {
  public static void main(I<? extends String> x) {
    x.foo(<caret>s -> null);
  }
}
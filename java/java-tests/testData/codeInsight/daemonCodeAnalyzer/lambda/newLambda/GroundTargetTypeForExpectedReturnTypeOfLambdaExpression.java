interface I<T, R> {
  R m(T t);
}

abstract class A<B> {
  public abstract A<B> bar(I<Throwable, ? extends A<? extends B>> resumeFunction);

  void foo(A<?> a) {
    a.bar(throwable ->  A.error());
  }

  public static final <T> A<T> error() {
    return null;
  }
}
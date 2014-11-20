class A<T> {
  A(T... t) {
  }

  {
    A<String> a = new A<>("a", "b");
    foo(new A<>("", ""));
    bar(new A<>("", ""));
    bar(new A<>(get()));
    bar(new A<>(get( ), ""));
  }

  void foo(A<String> s) {}
  <T> void bar(A<T> s) {}

  <K> K get() {return null;}

  <M> A<M> s(M... m) {
    return null;
  }

  {
    bar(s(get()));
    bar(s(get(), ""));
  }
}

class B<T>  {
  public B(T entity) {}
  public B(T entity, Integer... error){}

  void foo(final Integer generalError){
    B value = new B<>("", generalError);
  }
}
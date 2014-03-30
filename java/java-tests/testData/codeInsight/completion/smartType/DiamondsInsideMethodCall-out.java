class A<T> {
  A(T... t) {
  }

  {
    bar(new A<>(<caret>) );
  }

  <T> void bar(A<T> s) {}

  
}

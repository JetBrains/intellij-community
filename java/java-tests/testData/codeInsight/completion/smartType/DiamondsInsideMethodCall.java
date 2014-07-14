class A<T> {
  A(T... t) {
  }

  {
    bar(new <caret> );
  }

  <T> void bar(A<T> s) {}

  
}

class A<E> {
}

class B<T> extends A<T> {
  <E extends T> void f<caret>oo(E t) {
  }
}
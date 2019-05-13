class A<E> {
  <T extends E> void f<caret>oo(T t) {
  }
}

class B<T> extends A<T> {
}
class A<E> {
  static <T> void f<caret>oo(T t) {
  }
}

class B<T> extends A<T> {
}
class A<T> {
  T get() {
    return null;
  }
}
class B extends A<StringBuffer> {}
class C {
  void m(A<?> a) {
    if (a instanceof B) {
      StringBuffer s = a.<caret>
    }
  }
}

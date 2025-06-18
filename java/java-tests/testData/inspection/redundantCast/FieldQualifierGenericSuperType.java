class A<T> {
  T value;
}

class B extends A<String> {
  static String test(A<?> b) {
    return ((B) b).value;
  }
}
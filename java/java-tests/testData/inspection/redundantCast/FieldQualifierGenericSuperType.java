class A<T> {
  T value;
}

class B extends A<String> {
  static String test(A<?> b) {
    return ((B) b).value;
  }

  static Object test1(A<?> b) {
    return ((<warning descr="Casting 'b' to 'B' is redundant">B</warning>) b).value;
  }
}
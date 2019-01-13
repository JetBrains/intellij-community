class A<T> {
  static {
    A<String> a = new A<String>();
    <caret>a.getClass();
  }
}
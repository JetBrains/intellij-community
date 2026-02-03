class Base<T> {
  void m<caret>() {
  }
}

class A extends Base<String> {
  void x() {
    m();
  }
}
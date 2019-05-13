class A<T> {
  void foo() {
    T <selection>t</selection> = null;
  }
}
class B extends A<String> {
  void foo() {}
}
class A<T> {
  public A(T i, String z, String zz) {}
}
class B extends A<String> {
  public B(String i, String zz, String z) {
    super(<caret>);
  }
}
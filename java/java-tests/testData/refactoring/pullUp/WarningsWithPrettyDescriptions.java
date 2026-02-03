interface X  {

}
enum E implements X {
  A
}
enum F implements X {
  A {}
}
class Y implements X {
  void x<caret>() {}
}
record Z() implements X {
  void f() {
    new X() {};
  }
}
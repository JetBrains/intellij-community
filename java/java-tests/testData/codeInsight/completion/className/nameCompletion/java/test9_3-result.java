@interface MyObjectType {
  int param();
}
class A {
  void foo() {
    MyObjectType<caret>
  }
}
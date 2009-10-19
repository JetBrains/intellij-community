package aaa;
@interface MyObjectType {}
class A {
  void aaa() {
    getClass().getAnnotations(MyObject<caret>)
  }
}
class A {
  void foo(int <caret>i) {}
}
class B extends A {
  void foo() {}
  
  @Override
  void foo(int i) {
    
  }
}
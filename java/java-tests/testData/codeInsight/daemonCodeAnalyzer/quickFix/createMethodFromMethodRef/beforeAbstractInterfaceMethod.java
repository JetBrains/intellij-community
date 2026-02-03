// "Create method 'fooBar'" "true"
class FooBar {
  interface A {}
  void m(A a){
    Runnable r = a::foo<caret>Bar;
  }
}

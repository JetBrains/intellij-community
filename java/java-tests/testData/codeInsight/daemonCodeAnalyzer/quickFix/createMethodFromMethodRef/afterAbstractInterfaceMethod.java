// "Create method 'fooBar'" "true"
class FooBar {
  interface A {
      void fooBar();
  }
  void m(A a){
    Runnable r = a::fooBar;
  }
}

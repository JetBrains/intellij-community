
class Bar<T> {
  static void f<caret>oo() {
  }

  void m(){
    Bar.foo();
  }

  {
    Runnable r = Bar::foo;
  }

  static {
    Runnable r = Bar::foo;
  }
}

class Bar1 {
  void m() {
    Bar.foo();
  }
  {
    Runnable r = Bar::foo;
  }
}
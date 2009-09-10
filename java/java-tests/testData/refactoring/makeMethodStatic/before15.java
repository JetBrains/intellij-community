public class Foo {
  Foo getAnotherFoo() {}
  
  void <caret>tryMakeMeStatic(boolean b) {
    if (b) {
        getAnotherFoo().tryMakeMeStatic(!b);
    }
  }
}

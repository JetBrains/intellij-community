public class Foo {
  Foo getAnotherFoo() {}
  
  static void tryMakeMeStatic(Foo anObject, boolean b) {
    if (b) {
        Foo.tryMakeMeStatic(anObject.getAnotherFoo(), !b);
    }
  }
}

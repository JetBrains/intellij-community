public class Foo {

  public Foo() {
    Runnable r = new Runnable() {
      public void run() {
        Goo<String> g = new G<caret>
      }
    };
  }
}

abstract class Goo<T> {
  abstract void foo();
}

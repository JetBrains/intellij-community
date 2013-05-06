public class Foo {

  public Foo() {
    Runnable r = new Runnable() {
      public void run() {
        Goo<String> g = new Goo<String>() {
            @Override
            void foo() {
            }
        };
      }
    };
  }
}

abstract class Goo<T> {
  abstract void foo();
}

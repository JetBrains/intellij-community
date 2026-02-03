public class Foo {

  public Foo() {
    Runnable r = new Runnable() {
      public void run() {
        Goo<String> g = new Goo<String>() {
            @Override
            void foo() {
                <caret>
            }
        };
      }
    };
  }
}

abstract class Goo<T> {
  abstract void foo();
}

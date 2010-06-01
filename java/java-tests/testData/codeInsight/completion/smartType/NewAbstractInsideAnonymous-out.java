public class Foo {

  public Foo() {
    Runnable r = new Runnable() {
      public void run() {
        Goo<String> g = new Goo<String>() {
            @Override
            void foo() {
                <selection>//To change body of implemented methods use File | Settings | File Templates.</selection>
            }
        };
      }
    };
  }
}

abstract class Goo<T> {
  abstract void foo();
}

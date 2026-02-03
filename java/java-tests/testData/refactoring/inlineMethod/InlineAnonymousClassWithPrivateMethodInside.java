public class Foo {
  public static void f<caret>oo() {
    bar(new Runnable() {
      @Override
      public void run() {
        doRun();
      }

      private void doRun() {
        // Woo-hoo
      }
    });
  }

  public static void bar(final Runnable runnable) {
    runnable.run();
  }
}

class Bar {
  public static void main(String[] args) {
    Foo.foo();
  }
}
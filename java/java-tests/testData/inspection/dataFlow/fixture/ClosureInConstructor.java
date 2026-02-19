import org.jetbrains.annotations.Nullable;

class A {
  private @Nullable Object obj;

  A() {
    Runnable r = new Runnable() {
      @Override
      public void run() {
        if (obj == null) {
          System.out.println("oops");
        }
      }
    };
    obj = "hello";
    r.run();
  }

}

import org.jetbrains.annotations.TestOnly;

public class D {
  public void test() {
    new Runnable() {
      public void run() {
        new A().foo();
      }
    };
  }
}
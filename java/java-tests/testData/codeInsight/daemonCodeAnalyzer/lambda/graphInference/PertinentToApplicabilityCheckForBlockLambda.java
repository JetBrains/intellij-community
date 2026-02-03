
import java.util.concurrent.Callable;

class Test {

  public static void main(String[] args) throws Exception {
    Runnable iteration = compute(() -> {
      if (true) return () -> {};
      return null;
    });
    Runnable iteration2 = compute(() -> () -> {});
  }

  static <T> T compute(Callable<T> c) throws Exception {
    return c.call();
  }
}

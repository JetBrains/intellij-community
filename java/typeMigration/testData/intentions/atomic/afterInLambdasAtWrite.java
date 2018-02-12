// "Convert to atomic" "true"

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.*;

public class InLambdas
{
  public void test()
  {
    AtomicInteger x = new AtomicInteger();
    // Also active at write point if it causes a compilation error
    Runnable r1 = () -> x.getAndIncrement();
    Runnable r2 = () -> x.addAndGet(2);
    Runnable r3 = () -> x.updateAndGet(v -> v * 2);
    Runnable r4 = () -> x.set(5);
    System.out.println(x.updateAndGet(v -> v / 3));
    IntSupplier s = () -> {
      return x.updateAndGet(v -> v * 2);
    };
  }
}
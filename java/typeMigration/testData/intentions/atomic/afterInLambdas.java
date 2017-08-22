import java.util.concurrent.atomic.AtomicInteger;

// "Convert to atomic" "true"
public class InLambdas
{
  public void test()
  {
    AtomicInteger x = new AtomicInteger(0);
    Runnable r1 = () -> x.getAndIncrement();
    Runnable r2 = () -> x.addAndGet(2);
    Runnable r3 = () -> x.updateAndGet(v -> v * 2);
    Runnable r4 = () -> x.set(5);
  }
}
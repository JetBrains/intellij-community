// "Extract side effects" "true"
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
  public void test(AtomicInteger i) {
      i.incrementAndGet();
      i.incrementAndGet();
  }
}
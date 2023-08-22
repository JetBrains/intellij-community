// "Extract side effect" "true-preview"
import java.util.concurrent.atomic.AtomicInteger;

public class Main {
  public void test(AtomicInteger i) {
    i.incrementAndGet() <caret>+ 2;
  }
}
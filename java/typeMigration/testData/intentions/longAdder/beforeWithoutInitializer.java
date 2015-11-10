// "Convert variable to 'java.util.concurrent.atomic.LongAdder'" "true"
import java.util.concurrent.atomic.AtomicLong;

public class Main10 {
  void m() {
    AtomicLong <caret>l = new AtomicLong();

    String asString = l.toString();

    l.addAndGet(123);
  }
}

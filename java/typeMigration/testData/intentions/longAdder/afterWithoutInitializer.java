// "Convert variable to 'java.util.concurrent.atomic.LongAdder'" "true"
import java.util.concurrent.atomic.LongAdder;

public class Main10 {
  void m() {
    LongAdder l = new LongAdder();

    String asString = l.toString();

    l.add(123);
  }
}

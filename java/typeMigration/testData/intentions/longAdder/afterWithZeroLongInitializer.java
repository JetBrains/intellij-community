// "Convert variable to 'java.util.concurrent.atomic.LongAdder'" "true"
import java.util.concurrent.atomic.LongAdder;

public class Main10 {
  void m() {
    LongAdder l = new LongAdder();

    l.decrement();

    System.out.println(l.floatValue());

    l.add(12 - l.sum());
  }
}
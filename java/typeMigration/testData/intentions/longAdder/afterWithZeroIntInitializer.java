// "Convert variable to 'java.util.concurrent.atomic.LongAdder'" "true"
import java.util.concurrent.atomic.LongAdder;

public class Main10 {
  void m() {
    LongAdder i = new LongAdder();

    i.increment();

    double asDouble = i.doubleValue();

    System.out.println(i.sum());
  }
}
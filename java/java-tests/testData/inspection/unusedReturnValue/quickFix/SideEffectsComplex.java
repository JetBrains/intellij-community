import java.util.concurrent.atomic.AtomicInteger;

public class Main {
  private int in<caret>cOrDec(boolean b, AtomicInteger x) {
    return b ? x.incrementAndGet() : x.decrementAndGet();
  }

  public void test() {
    incOrDec(true, new AtomicInteger());
  }
}
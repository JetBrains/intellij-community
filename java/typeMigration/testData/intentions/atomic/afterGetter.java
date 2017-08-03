import java.util.concurrent.atomic.AtomicLong;

// "Convert to atomic" "true"
class Test {
    private final AtomicLong value = new AtomicLong(1);

  public long getValue() {
    return value.get();
  }

  public void process() {
    value.getAndIncrement();
  }
}
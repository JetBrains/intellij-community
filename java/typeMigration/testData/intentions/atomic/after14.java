import java.util.concurrent.atomic.AtomicLong;

// "Convert to atomic" "true"
class T {
  private final AtomicLong l = new AtomicLong(10L);

  public synchronized void update(long m) {
    l.set(m);
  }
}
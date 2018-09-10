import java.util.concurrent.atomic.AtomicInteger;

// "Convert to atomic" "true"
class X {
    private static final AtomicInteger count = new AtomicInteger(); // convert me
  private final int index;

  X() {
    count.getAndIncrement();
    index = count.get();
  }
}
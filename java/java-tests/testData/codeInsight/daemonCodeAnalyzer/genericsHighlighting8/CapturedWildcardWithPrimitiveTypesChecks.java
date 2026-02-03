import java.util.concurrent.atomic.AtomicReference;

class Test {
  private void test(AtomicReference<? extends Boolean> atomic) {
    boolean val = !atomic.get();
    boolean val1 = atomic.get() && atomic.get();
    assert atomic.get();
  }
}

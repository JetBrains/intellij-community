import java.util.concurrent.atomic.AtomicBoolean;
class Test {
  AtomicBoolean b;

  void foo() {
    if (b.get()) {
      b.set(true);
    }
  }
}
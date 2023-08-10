import java.util.concurrent.atomic.AtomicBoolean;

// "Convert to atomic" "true"
class A {
    final AtomicBoolean bool = new AtomicBoolean(false);
  void testAtomicBool() {
    bool.set(bool.get() | Math.random() > 0.5);
  }
}
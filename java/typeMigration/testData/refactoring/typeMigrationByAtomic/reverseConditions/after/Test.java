import java.util.concurrent.atomic.AtomicBoolean;
class Test {
  boolean b;

  void foo() {
    if (b) {
      b = true;
    }
  }
}
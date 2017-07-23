import java.util.concurrent.atomic.AtomicIntegerArray;

// "Convert to atomic" "true"
class Test {
    final AtomicIntegerArray ii = new AtomicIntegerArray(new int[12]);

  void m() {
    int k = ii.length();
  }
}

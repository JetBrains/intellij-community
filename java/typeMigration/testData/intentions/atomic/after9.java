import java.util.concurrent.atomic.AtomicInteger;

// "Convert to atomic" "true"
class Test {
    final AtomicInteger o = new AtomicInteger();
  int j = o.get();

  Test(int o) {
    this.o.set(o);
  }

  void foo() {
    while ((o.set(j)) != 0) {}
  }
}
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.IntUnaryOperator;

class Test {
  AtomicInteger i = new AtomicInteger(2015);

  void foo() {
    int k = i.incrementAndGet();

    i.compareAndSet(1, 2);

    i.updateAndGet(new IntUnaryOperator() {
      @Override
      public int applyAsInt(int operand) {
        return operand + 5;
      }
    });
  }
}
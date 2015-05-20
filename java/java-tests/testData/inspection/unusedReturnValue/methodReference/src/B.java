import java.util.function.Consumer;
import java.util.function.DoubleSupplier;

class B {

  public void test(Consumer<DoubleSupplier> consumer) {
    consumer.accept(this::method);
  }

  private double method() {
    return 1;
  }
}
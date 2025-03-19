import java.util.function.IntSupplier;

class Main {
  void test(int i) {
    throw new IndexOutOfBoundsException(i++<caret>);
  }
}
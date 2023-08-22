import java.util.function.IntSupplier;

public class Main {
  int i = 3;

  void foo() {
    IntSupplier supplier = () -> {
        int j = i;
        i++;
        return j;
    };
  }
}
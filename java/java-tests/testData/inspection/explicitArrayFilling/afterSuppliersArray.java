// "Replace loop with 'Arrays.fill()' method call" "true"
import java.util.Arrays;
import java.util.function.*;

class Test {

  private void testLambdas() {
    Supplier[] arr = new Supplier[10];
      Arrays.fill(arr, (Supplier) () -> new int[10]);
  }

}
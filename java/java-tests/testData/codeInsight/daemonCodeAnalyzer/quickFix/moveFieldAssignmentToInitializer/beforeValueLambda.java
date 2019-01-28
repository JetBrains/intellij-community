// "Move assignment to field declaration" "false"
import java.util.function.IntSupplier;

public class Main {
  int i;

  public void test() {
    IntSupplier r = () -> i <caret>= 1;
  }
}
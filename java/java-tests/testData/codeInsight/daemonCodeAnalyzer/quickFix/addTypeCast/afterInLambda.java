// "Cast expression to 'int'" "true-preview"
import java.util.*;
import java.util.function.*;

class Test {
  void test() {
    IntSupplier i = () -> (int) Math.PI;
  }
}

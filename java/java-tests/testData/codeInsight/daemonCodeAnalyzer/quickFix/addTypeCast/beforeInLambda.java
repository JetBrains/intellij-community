// "Cast to 'int'" "true"
import java.util.*;
import java.util.function.*;

class Test {
  void test() {
    IntSupplier i = () -> Math.<caret>PI;
  }
}

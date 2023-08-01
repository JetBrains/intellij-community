// "Replace with 'List[]::new'" "true"
import java.util.*;
import java.util.stream.*;

class X {
  void genericArrayCreation() {
    IntStream.range(0, 32)
      .mapToObj(i -> Arrays.asList(i))
      .toArray(Integer<caret>[]::new);
  }
}
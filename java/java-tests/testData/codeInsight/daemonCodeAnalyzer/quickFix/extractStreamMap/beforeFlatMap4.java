// "Extract variable 's' to 'mapToObj' operation" "true"
import java.util.*;
import java.util.stream.*;

public class Test {
  void testFlatMap() {
    IntStream.of(1, 2, 3).flatMap(x -> {
      String <caret>s = String.valueOf(x);
      return IntStream.range(0, s.length());
    }).forEach(System.out::println);
  }
}
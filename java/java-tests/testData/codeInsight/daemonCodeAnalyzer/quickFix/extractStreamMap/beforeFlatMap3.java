// "Extract variable 'y' to 'map' operation" "true-preview"
import java.util.*;
import java.util.stream.*;

public class Test {
  void testFlatMap() {
    Stream.of("xyz").flatMapToInt(x -> {
      Integer <caret>y = x.length();
      return IntStream.range(0, y);
    }).forEach(System.out::println);
  }
}
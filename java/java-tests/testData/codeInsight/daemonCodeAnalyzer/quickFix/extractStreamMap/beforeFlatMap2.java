// "Disable 'Extract to separate mapping method'" "false"
import java.util.*;
import java.util.stream.*;

public class Test {
  void testFlatMap() {
    // no direct method which would flatMap from long to int type, so intention is disabled here
    Stream.of("xyz").flatMapToInt(x -> {
      long <caret>y = x.length();
      return IntStream.range(0, (int) y);
    }).forEach(System.out::println);
  }
}
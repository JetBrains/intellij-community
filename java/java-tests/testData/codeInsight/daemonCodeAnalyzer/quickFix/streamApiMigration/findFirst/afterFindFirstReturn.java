// "Collapse loop with stream 'findFirst()'" "true-preview"

import java.util.Arrays;
import java.util.stream.Stream;

public class TestFile {
  public void test() {
      Stream.of("a", "b").filter(s -> !s.isEmpty()).findFirst().ifPresent(System.out::println);
  }
}
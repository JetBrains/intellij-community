// "Replace with findFirst()" "true-preview"

import java.util.Arrays;
import java.util.stream.Stream;

public class Main {
  public static String find() {
      //comment
      return Stream.of("foo", "bar", "baz").filter(s -> s.contains("z")).findFirst().orElse("");
  }
}
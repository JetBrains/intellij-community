// "Replace Arrays.asList().stream() with Arrays.stream()" "true-preview"

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

public class Main {
  public Stream<Object> stream() {
    Number[] numbers = {1, 2.0, 3};
    return Arrays.<Object>stream(numbers).filter(Objects::nonNull);
  }
}

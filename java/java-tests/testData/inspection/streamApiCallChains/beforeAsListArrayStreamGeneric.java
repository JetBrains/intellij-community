// "Replace Arrays.asList().stream() with Arrays.stream()" "true"

import java.util.Arrays;
import java.util.Objects;
import java.util.stream.Stream;

public class Main {
  public Stream<Object> stream() {
    Number[] numbers = {1, 2.0, 3};
    return Arrays.<Object>asList(numbers).st<caret>ream().filter(Objects::nonNull);
  }
}

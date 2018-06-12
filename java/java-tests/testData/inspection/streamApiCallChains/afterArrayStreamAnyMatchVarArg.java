// "Replace with Arrays.asList().contains()" "true"

import java.util.Arrays;
import java.util.stream.Stream;

public class Main {
  public boolean find(String key) {
    return Arrays.asList("foo", "bar", "baz").contains(key);
  }
}
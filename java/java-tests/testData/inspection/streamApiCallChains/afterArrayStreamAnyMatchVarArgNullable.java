// "Replace with 'List.contains()'" "true"

import java.util.Arrays;
import java.util.stream.Stream;

public class Main {
  public boolean find(String key, String addVal) {
    return Arrays.asList("foo", "bar", "baz", addVal).contains(key);
  }
}
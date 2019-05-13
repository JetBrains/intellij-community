// "Replace with 'List.contains()'" "true"

import java.util.List;
import java.util.stream.Stream;

public class Main {
  public boolean find(String key) {
    return List.of("foo", "bar", "baz").contains(key);
  }
}
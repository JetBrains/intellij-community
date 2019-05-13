// "Replace with 'List.contains()'" "true"

import java.util.stream.Stream;

public class Main {
  public boolean find(String key, String addVal) {
    return Stream.of("foo", "bar", "baz", addVal).anyMat<caret>ch(d -> key.equals(d));
  }
}
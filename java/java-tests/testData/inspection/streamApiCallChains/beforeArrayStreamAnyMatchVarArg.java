// "Replace with Arrays.asList().contains()" "true"

import java.util.stream.Stream;

public class Main {
  public boolean find(String key) {
    return Stream.of("foo", "bar", "baz").anyMat<caret>ch(d -> key.equals(d));
  }
}
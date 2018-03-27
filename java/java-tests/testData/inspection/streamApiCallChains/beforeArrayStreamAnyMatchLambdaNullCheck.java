// "Replace with Arrays.asList().contains()" "true"

import java.util.stream.Stream;

public class Main {
  public boolean find(String[] data, String key) {
    return Stream.of(data).anyMat<caret>ch(d -> d == null ? key == null : d.equals(key));
  }
}
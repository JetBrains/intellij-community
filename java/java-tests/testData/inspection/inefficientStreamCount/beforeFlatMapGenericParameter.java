// "Replace Stream.flatMap().count() with Stream.mapToLong().sum()" "true"

import java.util.List;
import java.util.Map;

public class Main {
  private String s;

  public Main(List<Map<String, String>> s) {
    long count = s.stream().<String>f<caret>latMap(map -> map.values().stream()).count();
  }
}
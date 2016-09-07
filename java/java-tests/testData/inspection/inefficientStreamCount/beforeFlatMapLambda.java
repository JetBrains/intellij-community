// "Replace Stream.flatMap().count() with Stream.mapToLong().sum()" "true"

import java.util.List;

public class Main {
  private String s;

  public Main(List<List<String>> s) {
    long count = s.stream().flatMap((str<caret>ings) -> {
      return (strings.stream());
    }).count();
  }
}
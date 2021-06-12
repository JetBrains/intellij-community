// "Replace with 'Stream.mapToLong().sum()'" "true"

import java.util.List;

public class Main {
  private String s;

  public Main(List<List<String>> s) {
    long count = s.stream().flatMap((strings) -> {
      return (strings.stream());
    }).cou<caret>nt();
  }
}
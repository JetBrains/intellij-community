// "Replace with 'Stream.mapToLong().sum()'" "true-preview"

import java.util.Collection;
import java.util.List;

public class Main {
  private String s;

  public Main(List<List<String>> s) {
    long count = s.stream().mapToLong(Collection::size).sum();
  }
}
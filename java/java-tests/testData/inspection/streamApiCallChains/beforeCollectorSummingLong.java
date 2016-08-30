// "Replace Stream.collect(summingLong()) with Stream.mapToLong().sum()" "true"

import java.util.List;
import java.util.stream.Collectors;

public class Main {
  public void remove(List<Integer> ints, List<String> data) {
    ints.remove(data.stream().collect(Collectors<caret>.summingLong(String::length)));
  }
}
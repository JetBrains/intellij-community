// "Replace Stream.collect(summingInt()) with Stream.mapToInt().sum()" "true"

import java.util.List;
import java.util.stream.Collectors;

public class Main {
  public void remove(List<Integer> ints, List<String> data) {
    ints.remove(data.stream().collect(Collectors<caret>.summingInt(String::length)));
  }
}
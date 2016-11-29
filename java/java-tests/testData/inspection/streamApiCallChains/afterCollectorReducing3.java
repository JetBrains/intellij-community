// "Replace Stream.collect(reducing()) with Stream.map().reduce()" "true"

import java.util.List;
import java.util.stream.Collectors;

public class Main {
  public int sum(List<String> data) {
    return data.stream().filter(x -> x.startsWith("xyz")).map(String::length).reduce(0, Integer::sum);
  }
}
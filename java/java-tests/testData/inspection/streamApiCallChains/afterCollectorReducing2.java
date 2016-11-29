// "Replace Stream.collect(reducing()) with Stream.reduce()" "true"

import java.util.List;
import java.util.stream.Collectors;

public class Main {
  public String concat(List<String> data) {
    return data.stream().filter(x -> x.startsWith("xyz")).reduce("", String::concat);
  }
}
// "Replace 'collect(reducing())' with 'reduce()' (may change semantics when result is null)" "true"

import java.util.List;
import java.util.stream.Collectors;

public class Main {
  public Optional<String> concat(List<String> data) {
    return data.stream().filter(x -> x.startsWith("xyz")).reduce(String::concat);
  }
}
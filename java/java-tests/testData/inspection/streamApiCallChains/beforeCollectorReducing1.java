// "Replace Stream.collect(reducing()) with Stream.reduce() (may change semantics when result is null)" "true"

import java.util.List;
import java.util.stream.Collectors;

public class Main {
  public Optional<String> concat(List<String> data) {
    return data.stream().filter(x -> x.startsWith("xyz")).colle<caret>ct(Collectors.reducing(String::concat));
  }
}
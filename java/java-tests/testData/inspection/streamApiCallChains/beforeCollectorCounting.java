// "Replace Stream.collect(counting()) with Stream.count()" "true"

import java.util.List;
import java.util.stream.Collectors;

public class Main {
  public long count(List<String> data) {
    return data.stream().filter(x -> x.startsWith("xyz")).collect(Collectors.<caret>counting());
  }
}
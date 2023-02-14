// "Replace 'collect(minBy())' with 'min()' (may change semantics when result is null)" "true-preview"

import java.util.List;
import java.util.stream.Collectors;

public class Main {
  public Optional<String> min(List<String> data) {
    return data.stream().filter(x -> x.startsWith("xyz")).collect(Collectors.minBy(Str<caret>ing.CASE_INSENSITIVE_ORDER));
  }
}
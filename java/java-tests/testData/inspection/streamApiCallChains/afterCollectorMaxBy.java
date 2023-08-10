// "Replace 'collect(maxBy())' with 'max()' (may change semantics when result is null)" "true-preview"

import java.util.List;
import java.util.stream.Collectors;

public class Main {
  public String max(List<String> data) {
    return data.stream().filter(x -> x.startsWith("xyz")).max(String.CASE_INSENSITIVE_ORDER).orElse("");
  }
}
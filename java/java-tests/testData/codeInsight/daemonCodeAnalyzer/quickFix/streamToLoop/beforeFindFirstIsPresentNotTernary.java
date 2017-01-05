// "Replace Stream API chain with loop" "true"

import java.util.List;
import java.util.Objects;

public class Main {
  String test(List<List<String>> strings) {
    return !strings.stream().filter(Objects::nonNull).flatMap(List::stream).fin<caret>dFirst().isPresent() ? "xyz" : "abc";
  }
}

// "Unwrap" "false"
import java.util.*;

public class Tests {
  void test(List<String> list) {
    // Will be reported as "Excessive lambda usage", changing to orElse which will trigger rewrapping inspection
    // no need to do this in single step
    Optional<String> opt = Optional.ofNullable(list.stream().filter(Objects::nonNull).findFirst().<caret>orElseGet(() -> null));
  }
}

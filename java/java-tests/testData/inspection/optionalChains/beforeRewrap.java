// "Unwrap" "true"
import java.util.*;

public class Tests {
  void test(List<String> list) {
    Optional<String> opt = Optional.ofNullable(list.stream().filter(Objects::nonNull).findFirst().<caret>orElse(null));
  }
}

// "Unwrap" "true"
import java.util.*;

public class Tests {
  void test(List<String> list) {
    Optional<String> opt = Optional.of<caret>Nullable(list.stream().filter(Objects::nonNull).findFirst().orElse(null));
  }
}

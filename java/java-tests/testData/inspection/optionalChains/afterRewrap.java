// "Unwrap" "true"
import java.util.*;

public class Tests {
  void test(List<String> list) {
    Optional<String> opt = list.stream().filter(Objects::nonNull).findFirst();
  }
}

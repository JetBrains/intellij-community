// "Unwrap" "true"
import java.util.*;

public class Tests {
  void test2(Optional<String> optional) {
    if (optional.isPresent()) {
      System.out.println(optional);
    }
  }
}

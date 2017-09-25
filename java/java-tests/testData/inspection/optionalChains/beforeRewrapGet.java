// "Unwrap" "true"
import java.util.*;

public class Tests {
  void test2(Optional<String> optional) {
    if (optional.isPresent()) {
      System.out.println(Optional.o<caret>f(optional.get()));
    }
  }
}

// "Unwrap" "false"
import java.util.*;

public class Tests {
  void test(Optional<String> optional, boolean b) {
    if (b || optional.isPresent()) {
      System.out.println(Optional.o<caret>f(optional.get()));
    }
  }
}

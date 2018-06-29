// "Replace Optional.isPresent() condition with functional style expression" "false"

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class Main {
  void test(Optional<String> foo) {
    int defaultValue = 0;
    double bar = foo.isPresent<caret>() ? foo.get().length() * 1.2 : defaultValue;
  }
}
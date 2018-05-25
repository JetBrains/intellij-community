// "Replace Optional.isPresent() condition with functional style expression" "GENERIC_ERROR_OR_WARNING"

import java.util.Arrays;
import java.util.List;
import java.util.Optional;

public class Main {
  void test(Optional<String> foo) {
    double bar = foo.map(s -> s.length() * 1.2).orElse(0.0);
  }
}
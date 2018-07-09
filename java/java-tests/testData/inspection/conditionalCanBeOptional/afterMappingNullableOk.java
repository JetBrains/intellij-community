// "Replace with Optional.ofNullable() chain" "GENERIC_ERROR_OR_WARNING"

import java.util.Optional;

class Test {
  String trim(String s) {
    return s.isEmpty() ? null : s.trim();
  }

  String select(String foo) {
    return Optional.ofNullable(foo).map(this::trim).orElse(null);
  }
}
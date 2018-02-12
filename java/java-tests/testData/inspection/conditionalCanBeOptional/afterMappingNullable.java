// "Replace with Optional.ofNullable() chain (may change semantics)" "INFORMATION"

import java.util.Optional;

class Test {
  String trim(String s) {
    return s.isEmpty() ? null : s.trim();
  }

  String select(String foo) {
    return Optional.ofNullable(foo).map(this::trim).orElse("");
  }
}
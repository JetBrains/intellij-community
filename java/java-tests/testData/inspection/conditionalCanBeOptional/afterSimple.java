// "Replace with Optional.ofNullable() chain" "GENERIC_ERROR_OR_WARNING"

import java.util.Optional;

class Test {
  String select(String foo, String bar) {
    return Optional.ofNullable(foo).orElse(bar);
  }
}
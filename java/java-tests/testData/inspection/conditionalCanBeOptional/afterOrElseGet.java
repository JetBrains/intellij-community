// "Replace with Optional.ofNullable() chain" "GENERIC_ERROR_OR_WARNING"

import java.util.Optional;

class Test {
  String getDefault() {
    return "";
  }

  String select(String foo) {
    return Optional.ofNullable(foo).map(String::trim).orElseGet(this::getDefault);
  }
}
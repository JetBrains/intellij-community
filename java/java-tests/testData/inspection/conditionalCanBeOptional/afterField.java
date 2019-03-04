// "Replace with Optional.ofNullable() chain" "GENERIC_ERROR_OR_WARNING"

import java.util.Optional;

class Test {
  String foo;
  
  String select() {
    return Optional.ofNullable(this.foo).map(String::trim).orElse("");
  }
}
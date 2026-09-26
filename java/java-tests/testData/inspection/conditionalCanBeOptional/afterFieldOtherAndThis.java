// "Replace with Optional.ofNullable() chain" "GENERIC_ERROR_OR_WARNING"

import java.util.Optional;

class Test {
  String foo;
  
  String select(Test other) {
    return Optional.ofNullable(foo).map(s -> other.foo.trim() + s.trim()).orElseGet(() -> other.foo + null);
  }
}
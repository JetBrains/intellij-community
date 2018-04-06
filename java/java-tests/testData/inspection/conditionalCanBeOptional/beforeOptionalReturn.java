// "Replace with Optional.ofNullable() chain" "GENERIC_ERROR_OR_WARNING"

import java.util.Optional;

class Test {
  interface V {}

  interface Type {
    V getValue();
  }

  // IDEA-179273
  public Optional<V> foo(Type arg) {
    return arg =<caret>= null ? Optional.empty() : Optional.of(arg.getValue());
  }
}
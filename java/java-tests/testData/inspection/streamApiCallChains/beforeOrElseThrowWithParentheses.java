// "Replace with 'orElseThrow'" "true"

import java.util.Optional;

class Main {
  native Optional<String> getOptional();

  void test() {
    getOptional().orElseGet<caret>(((() -> {
      throw new IllegalArgumentException();
    })));
  }
}
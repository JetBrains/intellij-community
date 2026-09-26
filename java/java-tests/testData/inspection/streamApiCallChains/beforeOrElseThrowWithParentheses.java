// "Replace with 'orElseThrow'" "true-preview"

import java.util.Optional;

class Main {
  native Optional<String> getOptional();

  void test() {
    getOptional().orElseGet<caret>(((() -> {
      throw new IllegalArgumentException();
    })));
  }
}
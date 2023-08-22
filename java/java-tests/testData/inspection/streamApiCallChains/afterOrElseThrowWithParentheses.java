// "Replace with 'orElseThrow'" "true-preview"

import java.util.Optional;

class Main {
  native Optional<String> getOptional();

  void test() {
    getOptional().orElseThrow(((IllegalArgumentException::new)));
  }
}
// "Replace Arrays.asList().stream() with Stream.of()" "true-preview"

import java.util.Arrays;
import java.util.stream.Stream;

class AsListLiteralStream {
  Stream<String> abc() {
    return Stream.of("a", "b", "c");
  }
}
// "Replace Arrays.asList().stream() with Stream.of()" "true"

import java.util.Arrays;
import java.util.stream.Stream;

class AsListLiteralStream {
  Stream<String> abc() {
    return Arrays.asList("a", "b", "c").stre<caret>am();
  }
}
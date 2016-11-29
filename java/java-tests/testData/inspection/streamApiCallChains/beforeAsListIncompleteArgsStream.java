// "Replace Arrays.asList().stream() with Stream.of()" "true"

import java.util.Arrays;
import java.util.stream.Stream;

class AsListIncompleteArgsStream {
  Stream<String> abc() {
    return Ar<caret>rays.asList("a", , ).stream();
  }
}
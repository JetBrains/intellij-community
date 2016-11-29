// "Replace Arrays.asList().stream() with Stream.of()" "true"

import java.util.stream.Stream;
import static java.util.Arrays.asList;

class AsListLiteralStreamStaticImport {
  String max() {
    return Stream.of("a", "b", "c").max(String::compareTo).orElse(null);
  }
}
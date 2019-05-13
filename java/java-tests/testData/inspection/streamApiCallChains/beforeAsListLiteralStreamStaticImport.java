// "Replace Arrays.asList().stream() with Stream.of()" "true"

import java.util.stream.Stream;
import static java.util.Arrays.asList;

class AsListLiteralStreamStaticImport {
  String max() {
    return asList("a", "b", "c").stre<caret>am().max(String::compareTo).orElse(null);
  }
}
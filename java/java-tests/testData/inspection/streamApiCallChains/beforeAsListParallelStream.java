// "Replace Arrays.asList().stream() with Arrays.stream()" "false"

import java.util.Arrays;

class AsListParallelStream {
  String max(String[] args) {
    return Arrays.asL<caret>ist(args).parallelStream().max(String::compareTo);
  }
}
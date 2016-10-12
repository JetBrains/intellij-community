// "Replace Arrays.asList().stream() with Arrays.stream()" "true"

import java.util.Arrays;

class AsListArrayStream {
  String max(String[] args) {
    return Arrays.stream(args).max(String::compareTo);
  }
}
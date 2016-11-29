// "Replace Arrays.asList().stream() with Arrays.stream()" "true"

import java.util.Arrays;

import static java.util.Arrays.asList;

class AsListArrayStreamStaticImport {
  String max(String[] args) {
    return Arrays.stream(args).max(String::compareTo);
  }
}
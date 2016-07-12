// "Replace Arrays.asList().stream() with Arrays.stream()" "true"

import static java.util.Arrays.asList;

class AsListArrayStreamStaticImport {
  String max(String[] args) {
    return asL<caret>ist(args).stream().max(String::compareTo);
  }
}
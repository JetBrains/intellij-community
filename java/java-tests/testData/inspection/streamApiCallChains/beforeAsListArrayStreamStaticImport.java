// "Replace Arrays.asList().stream() with Arrays.stream()" "true-preview"

import static java.util.Arrays.asList;

class AsListArrayStreamStaticImport {
  String max(String[] args) {
    return asList(args).st<caret>ream().max(String::compareTo);
  }
}
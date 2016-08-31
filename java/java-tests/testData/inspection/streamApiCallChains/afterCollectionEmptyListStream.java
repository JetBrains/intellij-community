// "Replace Collections.emptyList().stream() with Stream.empty()" "true"

import java.util.*;
import java.util.stream.Stream;

class CollectionEmptyListStream {
  Stream<String> stream(String[] args) {
    return args.length == 1 ? Stream.<String>empty() : Arrays.stream(args);
  }
}
// "Replace Collections.emptyList().stream() with Stream.empty()" "true"

import java.util.*;
import java.util.stream.Stream;

class CollectionEmptyListStream {
  Stream<String> stream(String[] args) {
    return args.length == 1 ? Collections.<String>emptyList().st<caret>ream() : Arrays.stream(args);
  }
}
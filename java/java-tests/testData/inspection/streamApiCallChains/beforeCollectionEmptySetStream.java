// "Replace Collections.emptySet().stream() with Stream.empty()" "true"

import java.util.*;
import java.util.stream.Stream;

class CollectionEmptySetStream {
  Stream<String> stream(String[] args) {
    return args.length == 1 ? Collections.<String>emptySet().st<caret>ream() : Arrays.stream(args);
  }
}
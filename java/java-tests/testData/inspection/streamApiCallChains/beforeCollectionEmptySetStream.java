// "Replace Collections.emptySet().stream() with Stream.empty()" "true"

import java.util.*;
import java.util.stream.Stream;

class CollectionEmptySetStream {
  Stream<String> stream(String[] args) {
    return args.length == 1 ? Col<caret>lections.<String>emptySet().stream() : Arrays.stream(args);
  }
}
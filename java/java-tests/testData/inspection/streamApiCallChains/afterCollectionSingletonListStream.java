// "Replace Collections.singletonList().stream() with Stream.of()" "true-preview"

import java.util.*;
import java.util.stream.Stream;

class CollectionSingletonListStream {
  Stream<String> stream(String[] args) {
    return Stream.of("xyz");
  }
}
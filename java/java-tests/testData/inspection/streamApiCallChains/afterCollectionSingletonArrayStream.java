// "Replace Collections.singleton().stream() with Stream.of()" "true"

import java.util.*;
import java.util.stream.Stream;

class CollectionSingletonArrayStream {
  Stream<String[]> stream(String[] args) {
    return Stream.<String[]>of(args);
  }
}
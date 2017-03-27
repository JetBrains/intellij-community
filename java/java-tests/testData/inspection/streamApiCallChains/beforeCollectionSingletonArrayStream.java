// "Replace Collections.singleton().stream() with Stream.of()" "true"

import java.util.*;
import java.util.stream.Stream;

class CollectionSingletonArrayStream {
  Stream<String[]> stream(String[] args) {
    return Collections.singleton(args).str<caret>eam();
  }
}
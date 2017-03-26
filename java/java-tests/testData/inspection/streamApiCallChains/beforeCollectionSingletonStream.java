// "Replace Collections.singleton().stream() with Stream.of()" "true"

import java.util.*;
import java.util.stream.Stream;

class CollectionSingletonStream {
  Stream<String> stream(String[] args) {
    return Collections.<String>singleton("xyz").st<caret>ream();
  }
}
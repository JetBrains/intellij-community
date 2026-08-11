// "Fix all 'Stream API call chain can be simplified' problems in file" "true"

import java.util.stream.*;

import static java.util.stream.Collectors.*;

class Test {
  void filterCases(Stream<String> stream) {
    boolean b1 = stream.filter(String::isEmpty).collect(toSet()).isEm<caret>pty();
    boolean b2 = !stream.filter(String::isEmpty).collect(toSet()).isEmpty();
    boolean b3 = stream.filter(String::isEmpty).collect(toUnmodifiableSet()).isEmpty();
    boolean b4 = stream.filter(String::isEmpty).toArray().length > 0;
  }

  void noFilterCases(Stream<String> stream) {
    boolean b1 = stream.collect(toSet()).isEmpty();
    boolean b2 = !stream.collect(toSet()).isEmpty();
    boolean b3 = stream.toArray().length > 0;
    boolean b4 = 0 < stream.toArray().length;
  }
}

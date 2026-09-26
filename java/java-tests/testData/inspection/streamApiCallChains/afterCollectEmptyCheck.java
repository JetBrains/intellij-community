// "Fix all 'Stream API call chain can be simplified' problems in file" "true"

import java.util.stream.*;

import static java.util.stream.Collectors.*;

class Test {
  void filterCases(Stream<String> stream) {
    boolean b1 = stream.noneMatch(String::isEmpty);
    boolean b2 = stream.anyMatch(String::isEmpty);
    boolean b3 = stream.noneMatch(String::isEmpty);
    boolean b4 = stream.anyMatch(String::isEmpty);
  }

  void noFilterCases(Stream<String> stream) {
    boolean b1 = stream.findFirst().isEmpty();
    boolean b2 = stream.findFirst().isPresent();
    boolean b3 = stream.findFirst().isPresent();
    boolean b4 = stream.findFirst().isPresent();
  }
}

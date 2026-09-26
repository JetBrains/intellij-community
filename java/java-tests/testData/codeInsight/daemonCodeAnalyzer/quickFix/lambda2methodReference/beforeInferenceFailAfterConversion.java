// "Replace lambda with method reference" "false"
import java.util.*;
import java.util.stream.*;

class Test {
  void test() {
    Set<String> lorem = Collections.unmodifiableSet(Stream.of("Lorem")
                                                      .collect(Collectors.toCollection(() -> new TreeSet<<caret>>())));
  }
}
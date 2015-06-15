import java.util.Map;
import java.util.stream.Stream;

import static java.util.stream.Collectors.toMap;

class Test {
  Map<String, String> foo(Stream<? extends String> stream) {
    return stream.collect(toMap(s -> s, s -> s));
  }
}


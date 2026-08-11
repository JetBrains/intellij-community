// "Replace 'collect().toArray()' with 'toArray()'" "true-preview"

import java.util.stream.*;

import static java.util.stream.Collectors.*;

class Test {
  Object[] test(Stream<String> stream) {
    return stream.toArray();
  }
}

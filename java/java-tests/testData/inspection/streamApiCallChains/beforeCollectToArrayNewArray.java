// "Replace 'collect().toArray()' with 'toArray()'" "true-preview"

import java.util.stream.*;

import static java.util.stream.Collectors.*;

class Test {
  String[] test(Stream<String> stream) {
    return stream.collect(toList()).toAr<caret>ray(new String[0]);
  }
}

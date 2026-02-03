// "Merge filter chain" "true-preview"

import java.util.stream.Stream;
class Test {
  void foo(Stream<String> stringStream ) {
    stringStream.filter(name -> name.startsWith("A") && name.length() > 1).findAny();
  }
}

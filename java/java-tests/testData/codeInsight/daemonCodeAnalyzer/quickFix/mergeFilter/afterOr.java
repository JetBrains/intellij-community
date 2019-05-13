// "Merge filter chain" "true"

import java.util.stream.Stream;
class Test {
  void foo(Stream<String> stringStream ) {
    stringStream.filter(name -> (name.startsWith("A") || name.startsWith("B")) && (name.length() > 3 || name.length() == 1)).findAny();
  }
}

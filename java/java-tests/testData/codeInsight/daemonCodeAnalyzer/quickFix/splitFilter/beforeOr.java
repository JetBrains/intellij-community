// "Split into filter chain" "false"

import java.util.stream.Stream;
class Test {
  void foo(Stream<String> stringStream ) {
    stringStream.filter(name -> name.startsWith("A") |<caret>| name.length() > 1).findAny();
  }
}

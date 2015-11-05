// "Split into filter's chain" "false"

import java.util.stream.Stream;
class Test {
  void foo(Stream<String> stringStream ) {
    stringStream.filter(name -> name.startsWith("A") &<caret>&).findAny();
  }
}

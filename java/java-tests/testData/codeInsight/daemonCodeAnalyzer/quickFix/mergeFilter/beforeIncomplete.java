// "Merge filter chain" "false"

import java.util.stream.Stream;
class Test {
  void foo(Stream<String> stringStream ) {
    stringStream.fi<caret>lter(name -> name.startsWith("A")).filter().findAny();
  }
}

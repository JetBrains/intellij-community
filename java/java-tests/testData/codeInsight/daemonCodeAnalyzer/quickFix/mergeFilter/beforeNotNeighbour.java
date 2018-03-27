// "Merge filter chain" "false"

import java.util.stream.Stream;
class Test {
  void foo(Stream<String> stringStream ) {
    stringStream.fil<caret>ter(name -> name.startsWith("A")).map(a -> a).filter(b -> true);
  }
}

// "Merge filter chain" "true-preview"

import java.util.stream.Stream;
class Test {
  void foo(Stream<String> stringStream ) {
    stringStream.filt<caret>er(name -> name.startsWith("A")).filter(a -> a.length() > 1).findAny();
  }
}

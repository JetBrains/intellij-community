// "Merge filter chain" "true"

import java.util.stream.Stream;
class Test {
  void foo(Stream<String> stringStream ) {
    stringStream.filt<caret>er(name -> name.startsWith("A") || name.startsWith("B"))
      .filter(a -> a.length() > 3 || a.length() == 1).findAny();
  }
}

// "Split into filter chain" "true-preview"

import java.util.stream.Stream;
class Test {
  void foo(Stream<String> stringStream ) {
    stringStream.filter(name -> name.startsWith("A")//starts with A 
    ).filter((name -> /*comment*/name.length() > 1)).findAny();
  }
}

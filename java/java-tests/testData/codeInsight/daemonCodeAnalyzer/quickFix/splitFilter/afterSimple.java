// "Split into filter's chain" "true"

import java.util.stream.Stream;
class Test {
  void foo(Stream<String> stringStream ) {
    stringStream.filter(name -> name.startsWith("A")//starts with A 
    ).filter(name -> name.length() > 1/*comment*/).findAny();
  }
}

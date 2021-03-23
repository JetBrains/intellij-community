// "Replace with 'stream.findAny().isEmpty()'" "true"

import java.util.stream.Stream;

class Test {
  boolean isEmpty(Stream<String> stream) {
    return /*a*/stream/*b*/./*c*/limit(42)/*d*/./*e*/c<caret>ount/*f*/(/*g*/) /*h*/== /*i*/0/*j*/;
  }
}
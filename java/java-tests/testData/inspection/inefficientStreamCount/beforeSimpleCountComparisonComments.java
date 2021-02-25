// "Replace 'stream.count() == 0' with 'stream.findAny().isEmpty()'" "true"

import java.util.stream.Stream;

class Test {
  boolean isEmpty(Stream<String> stream) {
    return /*a*/stream/*b*/./*c*/c<caret>ount/*d*/(/*e*/) /*f*/== /*g*/0/*h*/;
  }
}
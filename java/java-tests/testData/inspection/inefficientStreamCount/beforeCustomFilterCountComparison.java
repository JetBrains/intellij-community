// "Replace with 'stream.findAny().isEmpty()'" "true"

import java.util.stream.Stream;

class Test {
  boolean isEmpty(MyStream<String> myStream) {
    return myStream.filter(false).c<caret>ount() == 0;
  }
}

class MyStream<T> implements Stream<T> {
  public Stream<T> filter(boolean flag) {
    return this;
  }
}
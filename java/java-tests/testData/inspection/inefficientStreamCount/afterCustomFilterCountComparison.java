// "Replace with 'stream.findAny().isEmpty()'" "true"

import java.util.stream.Stream;

class Test {
  boolean isEmpty(MyStream<String> myStream) {
    return myStream.filter(false).findAny().isEmpty();
  }
}

class MyStream<T> implements Stream<T> {
  public Stream<T> filter(boolean flag) {
    return this;
  }
}
// "Replace with Stream.generate()" "true"

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.LongStream;

class Test {
  public void test() {
    LongStream.generate(() -> 42l).limit(10).filter(x -> x > 12);
  }
}
// "Replace with Stream.generate()" "true"

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;


class Test {
  public void test() {
    IntStream.generate(() -> 42).limit(10).filter(x -> x > 12);
  }
}
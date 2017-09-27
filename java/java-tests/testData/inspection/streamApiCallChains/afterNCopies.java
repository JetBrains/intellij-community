// "Replace with Stream.generate()" "true"

import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.IntStream;
import java.util.stream.Stream;

class Test {
  public void test(Object[] array) {
    Stream.generate(() -> new Object()).limit(10);
  }
}
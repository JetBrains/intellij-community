// "Replace with collect" "true"

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Test {
  static String test(List<String> list) {
    int BUFLENGTH = 42;
      String sb = IntStream.range(0, BUFLENGTH >> 1).mapToObj(i -> true ? "a" : "b").collect(Collectors.joining());
  }
}
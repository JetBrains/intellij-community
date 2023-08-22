// "Collapse loop with stream 'collect()'" "true-preview"

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Test {
  static String test(List<String> list) {
    int BUFLENGTH = 42;
    char CH = 'a';

    String sb = IntStream.range(0, BUFLENGTH >> 1).mapToObj(i -> "\u041b" + CH + 'i').collect(Collectors.joining());
  }
}
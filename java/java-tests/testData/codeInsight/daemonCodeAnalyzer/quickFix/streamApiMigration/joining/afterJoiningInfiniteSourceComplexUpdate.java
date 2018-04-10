// "Replace with collect" "true"

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main {
  public static int find(List<List<String>> list) {
      String sb = IntStream.iterate(0, i -> i + 23 * 11).filter(i -> i % 100 == 0).mapToObj(String::valueOf).collect(Collectors.joining());
  }
}
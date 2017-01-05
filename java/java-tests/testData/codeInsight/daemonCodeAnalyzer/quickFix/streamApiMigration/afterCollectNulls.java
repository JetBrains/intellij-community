// "Replace with collect" "true"

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class Main<T> {
  public static List<String> test() {
      List<String> strings = IntStream.range(0, 10).<String>mapToObj(x -> null).collect(Collectors.toList());
      return strings;
  }
}
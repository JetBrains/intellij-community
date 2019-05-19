// "Replace with collect" "true"

import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import static java.util.stream.Collectors.*;

public class Main<T> {
  public static List<String> test() {
    List<String> strings = IntStream.range(0, 10).<String>mapToObj(x -> null).collect(toList());
      return strings;
  }
}
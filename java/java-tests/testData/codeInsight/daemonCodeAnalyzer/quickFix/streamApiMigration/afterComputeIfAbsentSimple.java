// "Replace with collect" "true"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  private Map<Integer, List<String>> test(String... list) {
      Map<Integer, List<String>> map = Arrays.stream(list).collect(Collectors.groupingBy(String::length));
      return map;
  }
}

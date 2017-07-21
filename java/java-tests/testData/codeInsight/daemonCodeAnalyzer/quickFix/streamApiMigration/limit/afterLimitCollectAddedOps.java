// "Replace with collect" "true"

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

public class Main {
  List<String> test(Map<String, List<String>> map, int limit) {
      List<String> list = map.entrySet().stream().filter(entry -> entry.getValue() != null).filter(entry -> entry.getValue().stream().anyMatch(str -> str.contains("foo"))).limit(limit).map(Map.Entry::getKey).collect(Collectors.toList());
      return list;
  }
}

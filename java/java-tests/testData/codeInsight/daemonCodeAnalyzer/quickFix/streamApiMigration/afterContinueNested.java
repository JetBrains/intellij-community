// "Replace with collect" "true"

import java.util.*;
import java.util.stream.Collectors;

public class Main {

  public List<String> test(Map<String, List<String>> map) {
      List<String> result = map.entrySet().stream().filter(entry -> !entry.getKey().isEmpty()).map(Map.Entry::getValue).filter(Objects::nonNull).flatMap(Collection::stream).map(String::trim).filter(trimmed -> !trimmed.isEmpty()).collect(Collectors.toList());
      return result;
  }
}
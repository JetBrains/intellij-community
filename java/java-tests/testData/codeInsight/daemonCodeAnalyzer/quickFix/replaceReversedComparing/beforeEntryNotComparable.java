// "Replace with 'Map.Entry.comparingByValue(Comparator.reverseOrder())'" "false"

import java.util.*;
import java.util.stream.*;

public class Main {
  static class Value {}

  List<Map.Entry<Long, Value>> topEntries(Map<Long, Value> map) {
    return map.entrySet().stream()
      .sorted(<caret>Map.Entry.comparingByValue().reversed())
      .limit(10)
      .collect(Collectors.toList());
  }
}

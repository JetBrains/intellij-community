// "Replace with 'Map.Entry.comparingByValue(Comparator.reverseOrder())'" "true-preview"

import java.util.*;
import java.util.stream.*;

public class Main {
  List<Map.Entry<Long, Long>> topEntries(Map<Long, Long> map) {
    return map.entrySet().stream()
      .sorted(Map.Entry.comparingByValue(Comparator.reverseOrder()))
      .limit(10)
      .collect(Collectors.toList());
  }
}

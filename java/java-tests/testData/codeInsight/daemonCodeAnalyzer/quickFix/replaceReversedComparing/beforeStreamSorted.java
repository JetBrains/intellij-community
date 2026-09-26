// "Replace with 'Comparator.comparing(..., Comparator.reverseOrder())'" "true-preview"

import java.util.*;
import java.util.stream.*;

public class Main {
  List<Map.Entry<Long, Long>> topEntries(Map<Long, Long> map) {
    return map.entrySet().stream()
      .sorted(<caret>Comparator.comparing(Map.Entry::getValue).reversed())
      .limit(10)
      .collect(Collectors.toList());
  }
}

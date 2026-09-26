// "Replace with 'Comparator.comparing(..., Comparator.reverseOrder())'" "false"

import java.util.*;
import java.util.stream.*;

public class Main {
  List<Map.Entry<Long, Long>> topEntries(Map<Long, Long> map) {
    return map.entrySet().stream()
      .sorted(<caret>Comparator.comparing(Map.Entry::getValue, Comparator.<Long>naturalOrder()).reversed())
      .limit(10)
      .collect(Collectors.toList());
  }
}

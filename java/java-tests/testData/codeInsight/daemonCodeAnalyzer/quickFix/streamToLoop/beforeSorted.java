// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.*;
import java.util.stream.*;

public class Main {
  public List<String> testSorted(List<String> list) {
    return list.stream().filter(Objects::nonNull).sorted().map(String::trim).distinct().<caret>collect(Collectors.toList());
  }

  public List<String> testSortedComparator(List<String> list) {
    return list.stream().sorted(String.CASE_INSENSITIVE_ORDER).collect(Collectors.toList());
  }

  public List<String> testSortedToArray(List<String> list) {
    return list.stream().sorted().toArray(String[]::new);
  }
}
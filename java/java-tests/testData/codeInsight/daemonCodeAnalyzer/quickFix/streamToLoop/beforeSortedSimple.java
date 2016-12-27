// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.*;

public class Main {
  public List<String> testSorted(List<String> list) {
    return list.stream().sorted(String.CASE_INSENSITIVE_ORDER).<caret>collect(Collectors.toList());
  }
}
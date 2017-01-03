// "Replace Stream API chain with loop" "true"

import java.util.*;
import java.util.stream.*;

public class Main {
  public List<String> testSorted(List<String> list) {
    return list.stream().filter(Objects::nonNull).sorted().map(String::trim).distinct().<caret>collect(Collectors.toList());
  }
}
// "Replace Stream API chain with loop" "true"

import java.util.*;

public class Main {
  private static Optional<String> max(List<?> list) {
    return list.stream().filter(String[].class::isInstance).map(String[].class::cast).map(x -> x[0]).m<caret>ax(Comparator.naturalOrder());
  }
}

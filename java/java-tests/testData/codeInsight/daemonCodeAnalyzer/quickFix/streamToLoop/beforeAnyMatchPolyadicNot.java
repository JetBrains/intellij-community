// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;

public class Main {
  public boolean testCond(List<String> list) {
    return !list.stream().<caret>anyMatch(String::isEmpty) && list.stream().anyMatch(Objects::isNull);
  }
}
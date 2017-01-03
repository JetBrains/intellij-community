// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;

public class Main {
  public boolean testCond(List<String> list) {
    boolean x = !list.stream().<caret>anyMatch(String::isEmpty) && list.stream().anyMatch(Objects::isNull) && list.size() > 2;
    return x;
  }
}
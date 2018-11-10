// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;
import java.util.Objects;

public class Main {
  public boolean testCond(List<String> list) {
    return list.stream().anyMatch(String::isEmpty) && list.stream().any<caret>Match(Objects::isNull);
  }
}
// "Replace Stream API chain with loop" "true"

import java.util.List;

public class Main {
  public long test(List<String> list) {
    return list.stream().distinct().c<caret>ount();
  }
}
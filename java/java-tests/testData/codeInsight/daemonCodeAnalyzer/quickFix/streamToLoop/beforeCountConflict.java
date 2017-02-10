// "Replace Stream API chain with loop" "true"

import java.util.List;

public class Main {
  static class Count {
  }

  public long test(List<Count> count) {
    return count.stream().co<caret>unt();
  }
}
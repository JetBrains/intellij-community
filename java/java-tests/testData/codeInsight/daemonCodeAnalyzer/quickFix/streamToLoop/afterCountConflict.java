// "Replace Stream API chain with loop" "true"

import java.util.List;

public class Main {
  static class Count {
  }

  public long test(List<Count> count) {
      long result = 0L;
      for (Count count1 : count) {
          result++;
      }
      return result;
  }
}
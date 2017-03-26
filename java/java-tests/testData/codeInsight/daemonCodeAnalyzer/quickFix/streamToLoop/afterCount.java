// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.List;

public class Main {
  public long test(List<String> list) {
      long count = 0L;
      for (String s : list) {
          count++;
      }
      return count;
  }

  public void testAssign(List<String> list) {
      long x = 0L;
      for (String s : list) {
          x++;
      }
      System.out.println(x);
  }

  static class Count {
  }

  public long testNameConflict(List<Count> count) {
      long result = 0L;
      for (Count count1 : count) {
          result++;
      }
      return result;
  }

  public long testNoBlock(List<String> list) {
    if(!list.isEmpty()) {
        long count = 0L;
        for (String s : list) {
            count++;
        }
        return count;
    }
    return -1;
  }
}
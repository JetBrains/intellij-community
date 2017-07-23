// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.List;

public class Main {
  public long test(List<String> list) {
    return list.stream().coun<caret>t();
  }

  public void testAssign(List<String> list) {
    long x = list.stream().count();
    System.out.println(x);
  }

  static class Count {
  }

  public long testNameConflict(List<Count> count) {
    return count.stream().count();
  }

  public long testNoBlock(List<String> list) {
    if(!list.isEmpty()) return list.stream().count();
    return -1;
  }
}
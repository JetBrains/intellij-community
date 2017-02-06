// "Fix all 'Stream API call chain can be replaced with loop' problems in file" "true"

import java.util.Arrays;
import java.util.List;

public class Main {
  public static int test(List<String> list) {
    return list.stream().peek(s -> System.out.println(s)).mapToInt(l -> l.length()).s<caret>um();
  }

  public static LongSummaryStatistics testSummaryStatistics(List<String> list) {
    return list.stream().peek(s -> System.out.println(s)).mapToLong(l -> l.length()).summaryStatistics();
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList("aaa", "b", "cc", "dddd")));
    System.out.println(testSummaryStatistics(Arrays.asList("aaa", "b", "cc", "dddd")));
  }
}
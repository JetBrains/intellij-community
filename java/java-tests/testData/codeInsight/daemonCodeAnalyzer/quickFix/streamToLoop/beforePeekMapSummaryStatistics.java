// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;
import java.util.LongSummaryStatistics;

public class Main {
  public static LongSummaryStatistics test(List<String> list) {
    return list.stream().peek(s -> System.out.println(s)).mapToLong(l -> l.length()).summ<caret>aryStatistics();
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList("aaa", "b", "cc", "dddd")));
  }
}
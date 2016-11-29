// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Main {
  public static DoubleSummaryStatistics test(List<String> strings) {
      DoubleSummaryStatistics stat = new DoubleSummaryStatistics();
      for (String str : strings) {
          if (Objects.nonNull(str)) {
              stat.accept(str.length() / 2.0);
          }
      }
      return stat;
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList(null, null)));
    System.out.println(test(Arrays.asList("a", "bbb", "cc", "d", "eee", "")));
  }
}
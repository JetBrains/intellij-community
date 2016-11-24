// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.DoubleSummaryStatistics;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

public class Main {
  public static DoubleSummaryStatistics test(List<String> strings) {
    return strings.stream().filter(Objects::nonNull).colle<caret>ct(Collectors.summarizingDouble(str -> str.length()/2.0));
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList(null, null)));
    System.out.println(test(Arrays.asList("a", "bbb", "cc", "d", "eee", "")));
  }
}
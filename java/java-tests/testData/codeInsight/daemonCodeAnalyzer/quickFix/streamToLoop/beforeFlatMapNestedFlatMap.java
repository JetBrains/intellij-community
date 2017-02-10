// "Replace Stream API chain with loop" "true"

import java.util.IntSummaryStatistics;
import java.util.List;

import static java.util.Arrays.asList;

public class Main {
  public static IntSummaryStatistics test(List<List<List<String>>> list) {
    return list.stream().filter(l -> l != null).flatMap(l -> l.stream().filter(lst -> lst != null).flatMap(lst -> lst.stream())).mapToInt(str -> str.length()).summaryStatisti<caret>cs();
  }

  public static void main(String[] args) {
    System.out.println(test(asList(asList(asList("a", "bbb", "ccc")), asList(), null, asList(asList("z")))));
  }
}
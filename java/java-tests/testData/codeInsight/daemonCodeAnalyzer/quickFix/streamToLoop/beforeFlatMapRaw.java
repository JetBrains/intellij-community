// "Replace Stream API chain with loop" "false"

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

public class Main {
  private static List<Object> test(List<List> list) {
    return list.stream().<Object>flatMap(List::stream).coll<caret>ect(Collectors.toList());
  }

  public static void main(String[] args) {
    System.out.println(test(asList(asList("a", "d"), asList("c", "b"))));
  }
}
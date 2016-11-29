// "Replace Stream API chain with loop" "true"

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

public class Main {
  private static List<List<String>> test(List<List<List<String>>> list) {
    return list.stream().flatMap(List::stream).c<caret>ollect(Collectors.toList());
  }

  public static void main(String[] args) {
    System.out.println(test(asList(asList(asList("a", "d")), asList(asList("c"), asList("b")))));
  }
}
// "Replace Stream API chain with loop" "true"

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

public class Main {
  private static List<List<String>> test(List<List<List<String>>> list) {
      List<List<String>> result = new ArrayList<>();
      for (List<List<String>> lists : list) {
          for (List<String> strings : lists) {
              result.add(strings);
          }
      }
      return result;
  }

  public static void main(String[] args) {
    System.out.println(test(asList(asList(asList("a", "d")), asList(asList("c"), asList("b")))));
  }
}
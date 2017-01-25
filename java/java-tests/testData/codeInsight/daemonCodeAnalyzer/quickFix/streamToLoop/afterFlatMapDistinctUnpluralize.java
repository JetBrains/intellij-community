// "Replace Stream API chain with loop" "true"

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static java.util.Arrays.asList;

public class Main {
  private static long test(List<List<String>> nested) {
      long count = 0L;
      for (List<String> names : nested) {
          Set<String> uniqueValues = new HashSet<>();
          for (String name : names) {
              if (uniqueValues.add(name)) {
                  count++;
              }
          }
      }
      return count;
  }

  public static void main(String[] args) {
    System.out.println(test(asList(asList("a"), asList(null, "bb", "ccc"))));
  }
}
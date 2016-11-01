// "Replace Stream API chain with loop" "true"

import java.util.List;

import static java.util.Arrays.asList;

public class Main {
  private static long test(List<List<String>> nested) {
    return nested.stream().flatMap(names -> names.stream().distinct()).coun<caret>t();
  }

  public static void main(String[] args) {
    System.out.println(test(asList(asList("a"), asList(null, "bb", "ccc"))));
  }
}
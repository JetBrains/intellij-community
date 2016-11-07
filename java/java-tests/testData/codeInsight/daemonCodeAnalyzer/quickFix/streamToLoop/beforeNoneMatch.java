// "Replace Stream API chain with loop" "true"

import java.util.List;

import static java.util.Arrays.asList;

public class Main {
  public static boolean test(List<List<String>> list) {
    return list.stream().filter(x -> x != null).flatMap(x -> x.stream()).noneMat<caret>ch(str -> str.startsWith("a"));
  }

  public static void main(String[] args) {
    System.out.println(test(asList(asList(), asList("a"), asList("b", "c"))));
    System.out.println(test(asList(asList(), asList("d"), asList("b", "c"))));
  }
}
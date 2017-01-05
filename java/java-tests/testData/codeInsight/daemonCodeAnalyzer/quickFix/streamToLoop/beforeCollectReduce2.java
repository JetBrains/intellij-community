// "Replace Stream API chain with loop" "true"

import java.util.List;
import java.util.stream.Collectors;

import static java.util.Arrays.asList;

public class Main {
  public static String test(List<String> list) {
    return list.stream().coll<caret>ect(Collectors.reducing("", (a, b) -> a+b));
  }

  public static void main(String[] args) {
    System.out.println(test(asList("a", "b", "c")));
  }
}
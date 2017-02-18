// "Replace Stream API chain with loop" "true"

import java.util.List;
import java.util.Optional;

import static java.util.Arrays.asList;

public class Main {
  public static Optional<String> test(List<List<String>> list) {
    return list.stream().filter(x -> x != null).flatMap(x -> x.stream()).findAn<caret>y();
  }

  public static void main(String[] args) {
    System.out.println(test(asList(asList(), asList("a"), asList("b", "c"))));
  }
}
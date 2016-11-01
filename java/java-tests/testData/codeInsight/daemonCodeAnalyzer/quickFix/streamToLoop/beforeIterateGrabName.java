// "Replace Stream API chain with loop" "true"

import java.util.stream.Stream;
import java.util.stream.Collectors;
import java.util.List;

public class Main {
  public static List<String> test() {
    return Stream.iterate("", x -> x + "a").limit(20).co<caret>llect(Collectors.toList());
  }

  public static void main(String[] args) {
    System.out.println(test());
  }
}
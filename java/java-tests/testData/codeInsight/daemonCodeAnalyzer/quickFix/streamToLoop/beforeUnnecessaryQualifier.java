// "Replace Stream API chain with loop" "true"

import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class Test {
  static String doProcess(String s) {
    return s;
  }

  public static void main(String[] args) {
    List<String> list = Stream.of("a", "b", "c").map(Test::doProcess).col<caret>lect(Collectors.toList());
    System.out.println(list);
  }
}
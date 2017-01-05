// "Replace Stream API chain with loop" "true"

import java.util.List;
import java.util.function.Function;
import java.util.stream.Collectors;

public class Main {
  public void test(List<String> list) {
    System.out.println(list.stream().map(list.size() < 10 ? String::trim : Function.identity()).colle<caret>ct(Collectors.toList()));
  }
}

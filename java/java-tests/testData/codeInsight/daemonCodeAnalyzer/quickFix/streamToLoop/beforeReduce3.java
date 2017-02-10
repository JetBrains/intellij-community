// "Replace Stream API chain with loop" "true"

import java.util.List;

import static java.util.Arrays.asList;

public class Main {
  public static int test(List<String> list) {
    return list.stream().re<caret>duce(0, (a, b) -> a+b.length(), Integer::sum);
  }

  public static void main(String[] args) {
    System.out.println(test(asList("a", "b", "c")));
  }
}
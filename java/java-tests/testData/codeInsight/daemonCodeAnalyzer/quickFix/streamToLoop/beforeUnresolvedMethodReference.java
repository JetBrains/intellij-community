// "Replace Stream API chain with loop" "false"

import java.util.List;

import static java.util.Arrays.asList;

public class Main {
  private static int test(List<String> list) {
    return list.stream().mapToInt(Blahblah::size).su<caret>m();
  }

  public static void main(String[] args) {
    System.out.println(test(asList("a", "b", "c")));
  }
}
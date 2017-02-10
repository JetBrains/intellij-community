// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;

public class Main {
  public static int test(List<String> list) {
    return list.stream().peek(s -> System.out.println(s)).mapToInt(l -> l.length()).s<caret>um();
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList("aaa", "b", "cc", "dddd")));
  }
}
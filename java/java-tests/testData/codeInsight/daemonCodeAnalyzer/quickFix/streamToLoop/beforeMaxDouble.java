// "Replace Stream API chain with loop" "true"

import java.util.*;

public class Main {
  public static OptionalDouble test(List<String> strings) {
    return strings.stream().mapToDouble(String::length).m<caret>ax();
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList()));
    System.out.println(test(Arrays.asList("a", "bbb", "cc", "d")));
  }
}
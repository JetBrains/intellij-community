// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;

public class Main {
  private static String test(List<String> list) {
    return list == null ? // if list is null 
           null :  // return null
           list.stream().filter(str -> str.contains("x")).find<caret>First().orElse(null); // otherwise not null
  }

  public static void main(String[] args) {
    System.out.println(test(Arrays.asList("a", "b", "syz")));
  }
}
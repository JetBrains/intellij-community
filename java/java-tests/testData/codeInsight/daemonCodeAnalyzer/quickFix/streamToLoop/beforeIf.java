// "Replace Stream API chain with loop" "true"

import java.util.Arrays;
import java.util.List;

public class Main {
  private static void test(List<String> list) {
    if(list.stream().filter(x -> x != null).an<caret>yMatch(x -> x.startsWith("x"))) {
      System.out.println("Ok!");
    }
  }

  public static void main(String[] args) {
    test(Arrays.asList("a", "b", "xyz"));
  }
}
// "Replace with collect" "true"

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class Test {
  public static void test(List<String> data) {
      List<String> result = data.stream().takeWhile(s -> !s.isEmpty()).collect(Collectors.toList());
      System.out.println(result);
  }
}

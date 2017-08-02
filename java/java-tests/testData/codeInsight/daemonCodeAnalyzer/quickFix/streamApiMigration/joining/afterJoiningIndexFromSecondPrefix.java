// "Replace with collect" "true"

import java.util.List;
import java.util.stream.Collectors;

public class Test {
  public void test(List<String> list) {
      String sb = list.stream().limit(10).collect(Collectors.joining(",", "prefix", ""));
      System.out.println(sb);
  }
}
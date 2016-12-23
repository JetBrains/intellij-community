// "Replace Stream API chain with loop" "true"

import java.util.List;

public class Main {
  public void test(List<String> list) {
      long x = 0;
      for (String s : list) {
          x++;
      }
      System.out.println(x);
  }
}
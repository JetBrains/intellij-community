// "Replace Stream API chain with loop" "true"

import java.util.List;

public class Main {
  public void test(List<Integer> list) {
      boolean x = false;
      for (Integer i : list) {
          if (i <= 2) {
              x = true;
              break;
          }
      }
      if(x) {
      System.out.println("found");
    }
  }
}

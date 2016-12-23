// "Replace Stream API chain with loop" "true"

import java.util.List;

public class Main {
  public void test(List<Integer> list) {
    boolean x = !list.stream().all<caret>Match(i -> i > 2);
    if(x) {
      System.out.println("found");
    }
  }
}

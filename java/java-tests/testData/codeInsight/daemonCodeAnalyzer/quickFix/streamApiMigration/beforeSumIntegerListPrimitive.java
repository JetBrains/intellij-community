// "Replace with sum()" "true"

import java.util.List;

public class Main {
  public void testSumIntegerList(List<Integer> data) {
    int sum = 0;
    for(int i : dat<caret>a) {
      sum += i;
    }
  }
}
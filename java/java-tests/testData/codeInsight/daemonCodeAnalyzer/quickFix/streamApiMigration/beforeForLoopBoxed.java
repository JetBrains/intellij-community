// "Replace with collect" "true"

import java.util.ArrayList;
import java.util.List;

public class Main {
  public void testForLoop() {
    List<Integer> result = new ArrayList<>();
    for (in<caret>t i = 0; 10 >= i; i++) {
      result.add(i);
    }
  }
}
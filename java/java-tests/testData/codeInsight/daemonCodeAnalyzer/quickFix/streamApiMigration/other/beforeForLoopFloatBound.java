// "Replace with collect" "false"

import java.util.ArrayList;
import java.util.List;

public class Main {
  public void testForLoop(List<String> input) {
    List<Integer> result = new ArrayList<>();
    float s = (float)input.size();
    for (in<caret>t i = 0; i < s; i++) {
      result.add(input.get(i).length());
    }
  }
}
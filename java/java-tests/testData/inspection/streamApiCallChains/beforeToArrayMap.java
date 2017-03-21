// "Fix all 'Simplify stream API call chains' problems in file" "false"

import java.util.*;

class Test {
  public void testToArray(List<String> data) {
    Object[] array = data.stream().map(x -> x).to<caret>Array();
  }
}
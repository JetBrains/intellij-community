// "Replace 'stream().toArray()' with 'toArray()'" "true"

import java.util.*;

class Test {
  public void testToArray(List<String[]> data) {
      /*generate array*/
      String[][] array = data.subList(0, /*max number*/ 10).toArray(new String[0][]);
  }
}
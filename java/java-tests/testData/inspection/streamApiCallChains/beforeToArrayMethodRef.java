// "Replace 'stream().toArray()' with 'toArray()'" "true-preview"

import java.util.*;

class Test {
  public void testToArray(List<String> data) {
    Object[] array = data.stream().to<caret>Array(String[]::new);
  }
}
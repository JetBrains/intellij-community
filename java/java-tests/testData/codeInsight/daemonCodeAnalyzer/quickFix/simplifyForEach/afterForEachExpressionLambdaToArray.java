// "Replace with 'toArray()'" "true-preview"

import java.util.*;

public class Main {
  private void test(List<String> strs) {
      String[] arr = strs.stream().toArray();
  }
}

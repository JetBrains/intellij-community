// "Collapse loop with stream 'collect()'" "true-preview"

import java.util.List;

public class Main {
  public static int find(List<List<String>> list) {
    StringBuilder sb = new StringBuilder();
    for <caret> (int i = 0;; i++) {
      if(i % 100 == 0) {
        sb.append(i);
      }
    }
  }
}
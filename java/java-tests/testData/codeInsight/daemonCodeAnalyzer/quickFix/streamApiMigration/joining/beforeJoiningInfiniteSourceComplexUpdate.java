// "Replace with collect" "true"

import java.util.List;

public class Main {
  public static int find(List<List<String>> list) {
    StringBuilder sb = new StringBuilder();
    for <caret> (int i = 0;; i = i + 23 * 11) {
      if(i % 100 == 0) {
        sb.append(i);
      }
    }
  }
}
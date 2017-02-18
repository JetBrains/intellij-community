// "Replace Stream API chain with loop" "true"

import java.util.List;

public class Main {
  public long test(List<String> list) {
    if(!list.isEmpty()) {
        long count = 0;
        for (String s : list) {
            count++;
        }
        return count;
    }
    return -1;
  }
}
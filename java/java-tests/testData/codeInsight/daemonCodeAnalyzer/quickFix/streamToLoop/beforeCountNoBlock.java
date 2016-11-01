// "Replace Stream API chain with loop" "true"

import java.util.List;

public class Main {
  public long test(List<String> list) {
    if(!list.isEmpty()) return list.stream().coun<caret>t();
    return -1;
  }
}
// "Replace with collect" "true"

import java.util.*;

public class Main {
  private void test(List<String> strs) {
    List<String> other = new ArrayList<>();
    strs.stream().forEach<caret>(s -> {
      if(s.length() > 2) {
        other.add(s);
      }
    });
  }
}

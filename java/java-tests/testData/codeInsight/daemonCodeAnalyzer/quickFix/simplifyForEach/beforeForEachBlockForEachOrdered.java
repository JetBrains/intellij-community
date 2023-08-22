// "Extract intermediate operations" "true-preview"

import java.util.*;

public class Main {
  private void test() {
    List<String> other = new ArrayList<>();
    other.stream().forEachOrdere<caret>d(s -> {if(s.length() > 2) System.out.println(s);});
  }
}

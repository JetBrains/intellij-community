// "Replace with collect" "true"

import java.util.*;

public class Main {
  private void test() {
    List<String> strs = new ArrayList<>();
    List<String> other = new ArrayList<>();
    other.stream().forEach<caret>(s -> {strs.add(s)});
  }
}

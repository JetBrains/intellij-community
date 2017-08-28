// "Replace with collect" "true"

import java.util.*;

public class Main {
  private void test(List<String> other) {
    List<String> strs = new ArrayList<>();
    other.stream().forEach<caret>(s -> {strs.add(s)});
  }
}

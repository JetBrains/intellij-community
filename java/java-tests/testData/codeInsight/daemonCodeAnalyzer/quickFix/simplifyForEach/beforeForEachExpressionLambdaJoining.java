// "Replace with collect" "true"

import java.util.*;

public class Main {
  private void test() {
    List<String> strs = new ArrayList<>();
    StringBuilder sb = new StringBuilder();
    strs.stream().forEach<caret>(x -> sb.append(x));
  }
}

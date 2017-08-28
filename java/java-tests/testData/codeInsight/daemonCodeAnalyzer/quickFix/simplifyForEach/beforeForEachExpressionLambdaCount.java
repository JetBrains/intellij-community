// "Replace with count()" "true"

import java.util.*;

public class Main {
  private void test() {
    List<String> strs = new ArrayList<>();
    int count = 0;
    strs.stream().forEach<caret>(x -> count++)
  }
}

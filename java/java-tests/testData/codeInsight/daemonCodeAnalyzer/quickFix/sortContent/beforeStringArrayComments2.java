// "Sort content" "true"

import java.util.*;

public class Main {
  private void test() {
    String[] s = new String[]{"a",<caret> "c"//simple end comment
      , "b"};
  }
}

// "Collapse 'if' statement" "true"

import java.util.List;
import java.util.Map;

public class A {
  public void test(boolean b) {
    <caret>if (b) {
      System.out.println();
    }
    else System.out.println();
  }
}
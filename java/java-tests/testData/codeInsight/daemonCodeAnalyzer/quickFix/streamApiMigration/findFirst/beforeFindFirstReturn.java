// "Replace with findFirst()" "true"

import java.util.Arrays;

public class TestFile {
  public void test() {
    for(String s : Ar<caret>rays.asList("a", "b")) {
      if(!s.isEmpty()) {
        System.out.println(s);
        return;
      }
    }
  }
}
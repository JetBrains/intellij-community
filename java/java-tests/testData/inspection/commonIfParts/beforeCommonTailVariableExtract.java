// "Extract common part with variables from 'if'" "true"

import java.util.List;
import java.util.Map;

public class Main {

  private void work(int i){};

  public void test(int a, int b) {
    if<caret>(true) {
      int c = a + b;
      work(33);
      return c;
    } else {
      int c = a - b;
      work(12);
      return c;
    }
  }
}
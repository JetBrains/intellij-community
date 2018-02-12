// "Extract common part with variables from if " "true"

import java.util.List;
import java.util.Map;

public class Main {

  private void work(int i){};

  public int test(int a, int b) {
      int c;
      if(true) {
          c = a + b;
      } else {
          c = a - b;
      }
      work(1);
      return c;
  }
}
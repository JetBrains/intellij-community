// "Extract common part with variables from if " "false"

import java.util.List;
import java.util.Map;

public class Main {

  private void work(int i){};

  public int test(int a, int b) {
    if(true) {
      int c = a + b;
      work(1);
      return c;<caret>
    } else {
      work(2);
      int c = a - b;
      return c;
    }
  }
}
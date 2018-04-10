// "Extract common part from if (may change semantics)" "INFORMATION"

import java.util.List;
import java.util.Map;

public class Main {
  void doOther(int i) {}

  public void main(String[] args) {
    int x = 5;
      x++;
      if(x > 5) {
          doOther(1);
    } else {
          doOther(2);
    }
  }
}
// "Extract common part from if (may change semantics)" "INFORMATION"

import java.util.List;
import java.util.Map;

public class Main {
  void doOther(int i) {}

  public void main(String[] args) {
    int x = 5;
    if<caret>(x > 5) {
      x++;
      doOther(1);
    } else {
      x++;
      doOther(2);
    }
  }
}
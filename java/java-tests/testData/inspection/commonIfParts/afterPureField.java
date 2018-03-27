// "Extract common part from if (may change semantics)" "INFORMATION"

import java.util.List;
import java.util.Map;

public class Main {
  int field = 1;

  void incrementField() {
    field++;
  }

  void doOther(int i) {}

  public void main(String[] args) {
      incrementField();
      if(field < 2) {
          doOther(1);
    } else {
          doOther(2);
    }
  }
}
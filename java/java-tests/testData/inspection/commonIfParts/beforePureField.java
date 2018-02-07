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
    if<caret>(field < 2) {
      incrementField();
      doOther(1);
    } else {
      incrementField();
      doOther(2);
    }
  }
}
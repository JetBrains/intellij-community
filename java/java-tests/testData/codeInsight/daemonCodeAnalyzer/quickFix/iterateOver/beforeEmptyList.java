// "Disable 'Iterate'" "false"
import java.util.*;

class Test {
  void foo() {
    new ArrayList<><caret>();
  }
}
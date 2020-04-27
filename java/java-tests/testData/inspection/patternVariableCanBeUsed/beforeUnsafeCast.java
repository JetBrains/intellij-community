// "Replace 'list' with pattern variable" "false"
import java.util.*;

class X {
  void test(List obj) {
    if (obj instanceof ArrayList) {
      ArrayList<Integer> l<caret>ist = (ArrayList<Integer>) obj;
    }
  }
}
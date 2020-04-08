// "Replace 'list' with pattern variable" "true"
import java.util.*;

class X {
  void test(List<Integer> obj) {
    if (obj instanceof ArrayList) {
      ArrayList<Integer> l<caret>ist = (ArrayList<Integer>) obj;
    }
  }
}
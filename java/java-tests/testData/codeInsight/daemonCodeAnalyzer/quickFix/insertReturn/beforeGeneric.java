// "Insert 'return'" "true"
import java.util.*;

class Test {
  List<String> getList() {
    new ArrayList<>()<caret>
  }
}
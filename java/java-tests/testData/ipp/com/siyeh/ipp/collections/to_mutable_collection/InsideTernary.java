import java.util.*;

class Test {

  List<String> getList(int x) {
    return (x == 0 ? Collections.<caret>singletonList("0") : Collections.emptyList());
  }
}
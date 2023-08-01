// "Replace with 'List.subList().clear()'" "INFORMATION"
import java.util.List;

class Test {
  void removeTail(List<String> list, int max) {
    wh<caret>ile(list.size() > max) {
      list.remove(list.size() - 1);
    }
  }
}
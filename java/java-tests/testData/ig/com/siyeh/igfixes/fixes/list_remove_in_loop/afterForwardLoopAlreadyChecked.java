// "Replace with 'List.subList().clear()'" "GENERIC_ERROR_OR_WARNING"
import java.util.List;

class Test {
  void removeRange(List<String> list, int from, int to) {
    if(to <= from) return;

      list.subList(from, to).clear();
  }
}
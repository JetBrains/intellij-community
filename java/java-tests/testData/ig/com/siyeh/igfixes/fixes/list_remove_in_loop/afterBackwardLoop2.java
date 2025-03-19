// "Replace with 'List.subList().clear()'" "GENERIC_ERROR_OR_WARNING"
import java.util.List;

class Test {
  void removeRange(List<String> list, int limit) {
      if (list.size() > limit + 2) {
          list.subList(limit + 1, list.size() - 1).clear();
      }
  }
}
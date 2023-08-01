// "Replace with 'List.subList().clear()'" "GENERIC_ERROR_OR_WARNING"
import java.util.List;

class Test {
  void removeRange(List<String> list, int limit) {
    fo<caret>r (int i = list.size() - 2; i > limit; --i) {
      list.remove(i);
    }
  }
}
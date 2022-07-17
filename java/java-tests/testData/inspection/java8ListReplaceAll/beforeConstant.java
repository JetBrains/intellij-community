// "Replace the loop with 'List.replaceAll'" "true"
import java.util.List;

class Main {
  void modifyList(List<Integer> list) {
    Random random = new Random();
    for<caret> (int i = 0; i < list.size(); i++) {
      list.set(i, 42);
    }
  }
}
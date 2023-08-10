// "Replace the loop with 'List.replaceAll'" "true"
import java.util.List;

class Main {
  void modifyList(List<Integer> list) {
    Random random = new Random();
      list.replaceAll(ignored -> 42);
  }
}
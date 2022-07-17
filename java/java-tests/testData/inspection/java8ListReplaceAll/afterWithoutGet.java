// "Replace the loop with 'List.replaceAll'" "true"
import java.util.List;
import java.util.Random;

class Main {
  void modifyList(List<Integer> list) {
    Random random = new Random();
      list.replaceAll(ignored -> random.nextInt());
  }
}
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

class Main2 {
  // IDEA-221210
  public static void main(String[] args) {
    List<Integer> originalList = new ArrayList<>();
    originalList.add(1);
    final List<Integer> newList = originalList.stream()
      .collect(Collectors.toCollection(() -> new ArrayList<>(1)));

    boolean empty = newList.isEmpty();
  }

}

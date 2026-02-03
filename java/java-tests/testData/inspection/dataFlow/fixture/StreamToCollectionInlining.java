import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

class Main2 {
  // IDEA-264644
  void test() {
    List<String> list = new ArrayList<>();
    Stream.of("foo", "bar").collect(Collectors.toCollection(() -> list));
    if (list.isEmpty()) {}
  }
  
  // IDEA-221210
  public static void main(String[] args) {
    List<Integer> originalList = new ArrayList<>();
    originalList.add(1);
    final List<Integer> newList = originalList.stream()
      .collect(Collectors.toCollection(() -> new ArrayList<>(1)));

    boolean empty = newList.isEmpty();
  }

}

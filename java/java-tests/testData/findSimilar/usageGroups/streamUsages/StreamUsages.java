import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

public class CollectionsPlayGround {

  public static void main(String[] args) {
    List<String> list = new ArrayList<>();
    for (int i = 0; i < 100; i++) {
      list.add("entry:" + i);
    }

    final List<Integer> unmodifiableHashcodes = list.stream().map(String::hashCode).collect(Collectors.toUnmodifiableList());
    final List<Integer> modifiableHashcodes = list.stream().map(String::hashCode).collect(Collectors.toList());

    List<String> nonEmptyEntries = list.stream().filter(e -> e.length() > 0).collect(Collectors.toList());
    List<String> moreThanOneCharEntry = list.stream().filter(e -> e.length() > 1).collect(Collectors.toList());
    List<String> nonEmptyEntries2 = list.stream().filter(e -> e.isEmpty() ).collect(Collectors.toList());

    Set<String> nonEmptySet = list.stream().filter(e -> e.length() > 0).collect(Collectors.toSet());
  }
}

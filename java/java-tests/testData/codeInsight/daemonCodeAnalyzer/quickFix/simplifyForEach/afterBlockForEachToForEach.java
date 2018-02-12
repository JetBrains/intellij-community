// "Extract intermediate operations" "true"

import java.util.*;

public class Main {
  private void test() {
    List<String> other = new ArrayList<>();
      other.stream().filter(s -> s.length() > 2).forEach(System.out::println);
  }
}

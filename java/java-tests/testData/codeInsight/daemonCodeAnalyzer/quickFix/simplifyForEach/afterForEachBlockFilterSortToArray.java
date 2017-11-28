// "Replace with toArray" "true"

import java.util.*;

public class Main {
  private void test() {
      List<String> other = new ArrayList<>();
      String[] arr = other.stream().filter(s -> s.length() > 2).sorted(String.CASE_INSENSITIVE_ORDER).toArray();
  }
}

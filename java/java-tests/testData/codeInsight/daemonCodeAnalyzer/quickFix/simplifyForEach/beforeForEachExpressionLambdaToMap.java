// "Replace with collect" "true"

import java.util.*;

public class Main {
  private void test() {
    List<String> other = new ArrayList<>();
    HashMap<Integer, String> map = new HashMap<Integer, String>();
    other.stream().forEach<caret>(s -> map.putIfAbsent(s.length(), s));
  }
}

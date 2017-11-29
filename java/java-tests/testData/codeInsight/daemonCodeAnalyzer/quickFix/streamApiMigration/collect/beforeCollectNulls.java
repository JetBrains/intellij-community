// "Replace with collect" "true"

import java.util.ArrayList;
import java.util.List;

public class Main<T> {
  public static List<String> test() {
    List<String> strings = new ArrayList<>();
    for(in<caret>t x = 0; x < 10; x++) {
      strings.add(null);
    }
    return strings;
  }
}
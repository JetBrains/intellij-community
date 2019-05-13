// "Replace with collect" "true"

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Main<T> {
  public static List<Collection<String>> test(List<String> list) {
    List<Collection<String>> strings = new ArrayList<>();
    for (String s : l<caret>ist) {
      List<String> e = Collections.singletonList(s);
      strings.add(e);
    }
    return strings;
  }
}
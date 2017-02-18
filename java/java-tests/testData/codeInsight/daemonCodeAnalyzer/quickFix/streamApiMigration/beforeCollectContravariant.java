// "Replace with collect" "true"

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

public class Main<T> {
  public static List<Collection<CharSequence>> test(List<String> list) {
    List<Collection<CharSequence>> strings = new ArrayList<>();
    for (String s : lis<caret>t) {
      List<CharSequence> e = Collections.singletonList(s);
      strings.add(e);
    }
    return strings;
  }
}
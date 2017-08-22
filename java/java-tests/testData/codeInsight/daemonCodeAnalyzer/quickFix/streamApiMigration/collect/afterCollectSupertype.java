// "Replace with collect" "true"

import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class Main<T> {
  public static List<Collection<String>> test(List<String> list) {
      List<Collection<String>> strings = list.stream().map(Collections::singletonList).collect(Collectors.toList());
      return strings;
  }
}
// "Replace with collect" "true"

import java.util.*;
import java.util.stream.Collectors;

public class Main {
  public List<CharSequence> getListCharSequence(List<String> input) {
      List<CharSequence> result = input.stream().filter(s -> !s.isEmpty()).map(String::trim).collect(Collectors.toList());
      return result;
  }
}
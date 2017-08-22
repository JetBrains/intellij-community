// "Replace with collect" "true"

import java.util.*;

public class Main {
  public List<CharSequence> getListCharSequence(List<String> input) {
    List<CharSequence> result = new ArrayList<>();
    for(String s : in<caret>put) {
      if(!s.isEmpty()) {
        result.add(s.trim());
      }
    }
    return result;
  }
}
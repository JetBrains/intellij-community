// "Merge with previous 'map' call" "true"

import java.util.List;

public class Test {
  public boolean test(List<String> list) {
    return list.stream().map(String::isEmpty).none<caret>Match(b -> b);
  }
}
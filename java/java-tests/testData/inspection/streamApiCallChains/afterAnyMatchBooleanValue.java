// "Merge with previous 'map' call" "true"

import java.util.List;

public class Test {
  public boolean test(List<String> list) {
    /* ditto boolean!*/
      return list.stream().anyMatch(String::isEmpty);
  }
}
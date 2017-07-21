// "Replace with anyMatch()" "true"

import java.util.Collection;
import java.util.List;

public class Main {
  public boolean testAnyMatch(List<List<String>> data) {
    if (!data.isEmpty()) {
        if (data.stream().flatMap(Collection::stream).anyMatch(str -> !str.isEmpty())) {
            System.out.println("Found!");
        }
      return true;
    }
    return false;
  }

}
// "Replace with findFirst()" "true"

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

public class Main {
  public List<Integer> testFindFirstIfPresent(List<List<String>> data) {
    List<Integer> result = new ArrayList<>();
    if (!data.isEmpty()) {
        data.stream().flatMap(Collection::stream).filter(str -> !str.isEmpty()).findFirst().ifPresent(str -> result.add(str.length()));
    }
    return result;
  }

}
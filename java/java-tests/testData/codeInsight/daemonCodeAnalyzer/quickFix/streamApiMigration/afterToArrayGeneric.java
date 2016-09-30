// "Replace with toArray" "true"

import java.util.*;

public class Main {
  public List<?>[] testToArray(List<String> data) {
      return data.stream().filter(str -> !str.isEmpty()).map(Collections::singletonList).distinct().toArray(List<?>[]::new);
  }
}
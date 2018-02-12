// "Replace with toArray" "true"

import java.util.*;

public class Main {
  public Integer[] testNestedAnyMatch(List<List<String>> data) {
      return data.stream().filter(list -> list.stream().anyMatch(str -> !str.isEmpty())).map(List::size).toArray(Integer[]::new);
  }
}
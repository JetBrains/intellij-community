// "Replace with toArray" "true-preview"

import java.util.ArrayList;
import java.util.List;

public class Main {
  public Object[] testToArray(List<String> data) {
      return data.stream().filter(str -> !str.isEmpty()).toArray();
  }
}
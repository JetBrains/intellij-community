// "Replace with toArray" "true"

import java.util.ArrayList;
import java.util.List;

public class Main {
  public Object[] testToArray(List<String> data) {
      Object[] arr = data.stream().filter(str -> !str.isEmpty()).toArray();
    System.out.println(Arrays.toString(arr));
    return arr;
  }
}
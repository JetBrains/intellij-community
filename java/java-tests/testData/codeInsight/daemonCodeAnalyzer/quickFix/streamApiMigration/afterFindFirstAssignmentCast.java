// "Replace with findFirst()" "true"

import java.util.List;

public class Main {
  public void testCast(List<Object> data) {
      String found = (String) data.stream().filter(obj -> obj instanceof String).findFirst().orElse(null);
      System.out.println(found);
  }
}
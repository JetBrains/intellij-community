// "Replace with findFirst()" "true"

import java.util.List;

public class Main {
  public String testCast(List<Object> data) {
      return (String) data.stream().filter(obj -> obj instanceof String).findFirst().orElse(null);
  }
}
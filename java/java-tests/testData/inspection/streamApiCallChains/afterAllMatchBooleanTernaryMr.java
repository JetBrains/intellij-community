// "Merge with previous 'map' call" "true"

import java.util.List;
import java.util.function.Function;

public class Test {
  public <T extends Boolean> boolean test(List<String> list, boolean b, boolean b2) {
    return list.stream().allMatch(b ? String::isEmpty : b2 ? "foo"::equals : "bar"::equals);
  }
}
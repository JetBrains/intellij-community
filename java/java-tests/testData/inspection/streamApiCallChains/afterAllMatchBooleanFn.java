// "Merge with previous 'map' call" "true"

import java.util.List;
import java.util.function.Function;

public class Test {
  public <T extends Boolean> boolean test(List<String> list, Function<String, T> fn) {
    return list.stream().allMatch(fn::apply);
  }
}
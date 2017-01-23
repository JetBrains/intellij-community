// "Merge with previous 'map' call" "true"

import java.util.List;
import java.util.function.Function;

public class Test {
  public <T extends Boolean> boolean test(List<String> list, Function<String, T> fn, Function<String, Boolean> fn2, boolean b, boolean b2) {
    return list.stream().allMatch((b ? fn : b2 ? fn2 : fn)::apply);
  }
}
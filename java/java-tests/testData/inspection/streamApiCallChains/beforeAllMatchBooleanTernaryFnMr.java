// "Merge with previous 'map' call" "false"

import java.util.List;
import java.util.function.Function;

public class Test {
  public <T extends Boolean> boolean test(List<String> list, Function<String, T> fn, boolean b) {
    // neither "b ? String::isEmpty : fn" nor "(b ? String::isEmpty : fn)::apply" can serve as predicate
    // all possible replacements are longer
    return list.stream().map(b ? String::isEmpty : fn).all<caret>Match(Boolean::booleanValue);
  }
}
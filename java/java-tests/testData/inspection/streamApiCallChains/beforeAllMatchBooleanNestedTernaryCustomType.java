// "Merge with previous 'map' call" "false"

import java.util.List;
import java.util.function.Function;

public class Test {
  interface MyFunction extends Function<Object, Boolean> {};

  MyFunction fn3 = "xyz"::equals;

  public <T extends Boolean> boolean test(List<String> list, Function<String, T> fn, Function<String, Boolean> fn2, boolean b, boolean b2) {
    // neither "b ? fn : b2 ? fn2 : fn3" nor "(b ? fn : b2 ? fn2 : fn3)::apply" can serve as predicate
    // all possible replacements are longer
    return list.stream().map(b ? fn : b2 ? fn2 : fn3).all<caret>Match(Boolean::booleanValue);
  }
}
import org.jspecify.annotations.NullMarked;

import java.util.function.Function;
import java.util.List;

public class JSpecifyLambdaTernary {
  static <T, R> R process(List<T> list, Function<? super T, ? extends R> fn) {
    return fn.apply(list.get(0));
  }

  @NullMarked
  static class Use {
    static void processList(List<Integer> list) {
      Integer result = process(list, s -> s == 0 ? null : s);
      if (result == null) {}
    }

    static void processListNonNumeric(List<String> list) {
      String result = process(list, s -> s.isEmpty() ? null : s);
      if (result == null) {}
    }
  }
}
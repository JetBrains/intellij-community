// "Replace Optional.isPresent() condition with functional style expression" "GENERIC_ERROR_OR_WARNING"

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public class Main {

  public void test(Optional<String> opt, Function<String, Object> onPresent, Supplier<Object> onEmpty) {
    // warning level: no semantics change
    Object o = opt.is<caret>Present() ? onPresent.apply(opt.get()) : null;
  }
}
// "Replace Optional.isPresent() condition with functional style expression" "INFORMATION"

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public class Main {

  public void test(Optional<String> opt, Function<String, Object> onPresent, Supplier<Object> onEmpty) {
    // information level: could be semantic change if onPresent returns null
    Object o = opt.is<caret>Present() ? onPresent.apply(opt.get()) : onEmpty.get();
  }
}
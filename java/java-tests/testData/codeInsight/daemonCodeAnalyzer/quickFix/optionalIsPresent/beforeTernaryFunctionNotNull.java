// "Replace Optional presence condition with functional style expression" "GENERIC_ERROR_OR_WARNING"

import org.jetbrains.annotations.NotNull;

import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

public class Main {

  public void test(Optional<String> opt, Function<String, @NotNull Object> onPresent, Supplier<Object> onEmpty) {
    Object o = opt.i<caret>sPresent() ? onPresent.apply(opt.get()) : onEmpty.get();
  }
}
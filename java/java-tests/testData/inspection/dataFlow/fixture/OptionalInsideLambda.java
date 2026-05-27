import org.jetbrains.annotations.Nullable;

import java.util.Optional;
import java.util.function.Function;

final class NullableInspections {

  private interface Wrapper {
    <T> T withValue(Function<String, T> f);
  }

  @Nullable
  private final String optional1(Wrapper wrapper) {
    return wrapper.withValue(v ->
                               Optional.ofNullable(v.length() == 5 ? "5" : null).orElse(null)
    );
  }

  @Nullable
  private final String resolveOptional1(Wrapper wrapper) {
    return wrapper.withValue(v -> {
      String maybeNull = Optional.ofNullable(v.length() == 5 ? "5" : null).orElse(null);
      return maybeNull;
    });
  }

  @Nullable
  private final String optional2(Wrapper wrapper) {
    return wrapper.withValue(v -> Optional.of(v)
      .filter(s -> s.length() == 5)
      .map(_ -> "5")
      .orElse(null)
    );
  }

  @Nullable
  private final String resolveOptional2(Wrapper wrapper) {
    return wrapper.withValue(v -> {
      //noinspection UnnecessaryLocalVariable
      String maybeNull = Optional.of(v)
        .filter(s -> s.length() == 5)
        .map(_ -> "5")
        .orElse(null);
      return maybeNull;
    });
  }
}
import java.util.Optional;

import org.eclipse.jdt.annotation.NonNullByDefault;
import org.eclipse.jdt.annotation.Nullable;

// IDEA-291604
@NonNullByDefault
class Test {
  public @Nullable String test(final Optional<String> optional, final @Nullable String fallback) {
    return optional.orElse(fallback);
  }
}
import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;

@NullMarked
class Main {
  HashMap<String, String> map = new HashMap<>();

  public @Nullable String example() {
    return map.getOrDefault("key", null);
  }
}
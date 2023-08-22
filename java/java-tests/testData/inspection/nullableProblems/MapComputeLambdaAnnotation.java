import typeUse.NotNull;
import typeUse.Nullable;

import java.util.HashMap;
import java.util.Map;

public class MapComputeLambdaAnnotation {
  public static void main(final String[] args) {
    final Map<String, @NotNull String> test = new HashMap<>();

    test.compute("first", (String a, @Nullable String b) -> {
      assert b == null;

      return null;
    });
  }
}
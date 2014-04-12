import org.jetbrains.annotations.Nullable;

import java.util.List;

class Foo {

  public static void main(@Nullable Boolean hasMissingGems, List<String> missing) {
    if (hasMissingGems != null && hasMissingGems) {
      return;
    }

    if (hasMissingGems == null && missing.size() > 0) {

    }

  }
  public static void main2(@Nullable Boolean hasMissingGems, List<String> missing) {
    if ((hasMissingGems != null && hasMissingGems) || (hasMissingGems == null && missing.size() > 0)) {

    }

  }
}
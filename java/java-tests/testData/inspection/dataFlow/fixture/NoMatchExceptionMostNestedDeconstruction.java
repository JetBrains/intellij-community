import org.jetbrains.annotations.Nullable;

class Test2 {
  private static void testSwitchPattern() {
    final var full = new Level1(new Level2(new Level3(null)));
    System.out.println(getLevel3(full));
  }

  @Nullable
  private static String getLevel3(@Nullable final Level1 level1) {
    return switch (level1) {
      case Level1(Level2(Level3(var something))) -> something;
      case null -> null;
    };
  }

  record Level3(@Nullable String text) {
  }

  record Level2(Level3 something) {
  }

  record Level1(Level2 level2) {
  }
}
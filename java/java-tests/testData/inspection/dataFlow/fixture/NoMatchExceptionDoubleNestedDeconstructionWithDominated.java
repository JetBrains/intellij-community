import org.jetbrains.annotations.Nullable;

class Test2 {
  private static void testSwitchPattern() {
    final var nullLevel3 = new Level1(new Level2(null));
    System.out.println(getLevel3(nullLevel3));
  }

  @Nullable
  private static String getLevel3(@Nullable final Level1 level1) {
    return switch (level1) {
      case Level1(Level2(Level3(var something))) -> something;
      case Level1(Level2 t) -> "some";
      case null -> null;
    };
  }

  record Level3(String text) {
  }

  record Level2(@Nullable Level3 something) {
  }

  record Level1(Level2 level2) {
  }
}
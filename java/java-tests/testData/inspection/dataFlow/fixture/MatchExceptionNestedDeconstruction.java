import org.jetbrains.annotations.Nullable;

class Test {
  private static void testSwitchPattern() {
    final var nullLevel2 = new Level1(null);
    System.out.println(getLevel3(null));
    System.out.println(getLevel3(nullLevel2));
  }

  @Nullable
  private static Level3 getLevel3(@Nullable final Level1 level1) {
    return switch (level1) {
      case Level1(<warning descr="Pattern matching may throw 'MatchException'">Level2(var something)</warning>) -> something;
      case null -> null;
    };
  }

  static class Level3 {
  }

  record Level2(Level3 something) {
  }

  record Level1(@Nullable Level2 level2) {
  }
}
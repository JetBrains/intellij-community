import org.jetbrains.annotations.Nullable;

class TestExample {
  sealed interface II {
    record AI() implements II {}

    record BI() implements II {}
  }

  record RI(@Nullable II value) {}

  private static II getII(RI ri) {
    return switch (ri) {
      case RI(<warning descr="Pattern matching may throw 'MatchException'">II.BI bi</warning>) -> bi;
      case RI(II.AI ai) -> ai;
    };
  }
}

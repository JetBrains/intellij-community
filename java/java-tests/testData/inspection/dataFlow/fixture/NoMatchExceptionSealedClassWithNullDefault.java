import org.jetbrains.annotations.Nullable;

class TestExample {
  sealed interface II {
    record AI() implements II {}

    record BI() implements II {}
  }

  record RI(@Nullable II value) {}

  @Nullable
  private static II getII(RI ri) {
    return switch (ri) {
      case RI(II.BI bi) -> bi;
      case RI(II.AI ai) -> ai;
      case null, default -> null;
    };
  }
}

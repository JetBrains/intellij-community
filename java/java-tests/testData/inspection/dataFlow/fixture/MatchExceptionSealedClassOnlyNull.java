import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class TestExample {
  sealed interface II {
    record AI() implements II {}
    record BI() implements II {}
  }

  record RI(@Nullable II value) {}

  @Nullable
  private static II getII() {
    RI ri = createRi();
    if(ri.value != null) return null;
    return switch (ri) {
      case RI(<warning descr="Pattern matching will throw 'MatchException'">II.BI bi</warning>) -> bi;
      case RI(II.AI ai) -> ai;
    };
  }

  private static @NotNull RI createRi() {
    return new RI(null);
  }

  private static II getII2() {
    RI ri = createRi();
    return switch (ri) {
      case RI(II.BI bi) -> bi;
      case RI(II.AI ai) -> ai;
    };
  }
}

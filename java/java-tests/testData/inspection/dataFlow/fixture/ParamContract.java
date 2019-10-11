import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

class TestCls {
  public String broken(@NotNull Getter dowGetter) {
    final DayOfWeek dowOrDefault = firstNonNull(dowGetter.getDow(""), DayOfWeek.MONDAY);
    return dowOrDefault.name();
  }

  public String correct(@NotNull Getter dowGetter) {
    final DayOfWeek dowOrDefault = firstNonNull(dowGetter.getDow(), DayOfWeek.MONDAY);
    return dowOrDefault.name();
  }

  @Nullable
  @Contract(value = "!null, _ -> param1; null, !null -> param2; null, null -> null", pure = true)
  public static <T> T firstNonNull(@Nullable T value1, @Nullable T value2) {
    return value1 == null ? value2 : value1;
  }

  public interface Getter {
    @Nullable
    DayOfWeek getDow();
    @Nullable
    DayOfWeek getDow(String param);
  }

  enum DayOfWeek {
    MONDAY
  }
}
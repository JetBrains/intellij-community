import org.jetbrains.annotations.NotNull;

public class MultipleReturns {
  @NotNull public Object test(int i) {
    if (i == 0) return null;
    if (i == 1) return null;
    return null;
  }
}
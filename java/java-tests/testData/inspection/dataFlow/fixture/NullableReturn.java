import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;

class NullableReturn {
  @NotNull Object test(Object o, Object o2, Object o3) {
    Object x = o == null ? o3 : o2;
    // no nullable return from notnull method here
    return x == null ? o3 : x;
  }
}
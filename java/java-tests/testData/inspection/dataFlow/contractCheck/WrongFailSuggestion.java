import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;
import org.jetbrains.annotations.NotNull;
import java.util.Map;

class Foo {
  // IDEA-196563
  @Nullable
  @Contract(pure = true, value = "null, _, true -> fail; _, _, true -> !null")
  private static Object getParam(@Nullable Map<String, Object> params, @NotNull String paramName, boolean required) {
    final Object value = params == null ? null : params.get(paramName);
    if (value == null && required) {
      throw new IllegalArgumentException("Parameter not found");
    }
    return value;
  }
}

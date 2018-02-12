import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.io.IOException;

public class FlushVariableOnStackToNotNullType {
  private String str;

  @Contract(value = "null -> true", pure = true)
  public static boolean isEmpty(@Nullable String s) {
    return s == null || s.equals("");
  }

  interface State {
    @Nullable
    String get() throws IOException;
  }

  @Nullable
  public String getMessage(State currentState, boolean x, @NotNull String message) {
    String errorMessage = null;
    if (x) {
      errorMessage = message;
    }

    try {
      errorMessage = currentState.get();
    } catch (IOException ignored) {
    }

    if (isEmpty(errorMessage) && !isEmpty(str)) {
      errorMessage = "foo";
    }

    if (isEmpty(errorMessage)) {
      try {
        errorMessage = currentState.get();
      } catch (IOException ignored) {
      }
    }

    return errorMessage;
  }
}

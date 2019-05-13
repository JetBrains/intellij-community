import org.jetbrains.annotations.NotNull;

public class SimpleParamWithMessage {
  public void test(@NotNull("SimpleParamWithMessage.test(o) cant be null") Object o) {
  }
}
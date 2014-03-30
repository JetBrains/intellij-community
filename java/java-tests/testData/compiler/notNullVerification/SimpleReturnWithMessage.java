import org.jetbrains.annotations.NotNull;

public class SimpleReturnWithMessage {
  @NotNull("This method cannot return null")
  public Object test() {
    return null;
  }
}
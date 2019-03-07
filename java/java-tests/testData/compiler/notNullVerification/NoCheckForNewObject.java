import org.jetbrains.annotations.NotNull;

public class NoCheckForNewObject {
  @NotNull
  Object method() { return new Object(); }
}
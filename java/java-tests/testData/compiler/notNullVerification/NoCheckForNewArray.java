import org.jetbrains.annotations.NotNull;

public class NoCheckForNewArray {
  @NotNull
  Object method() { return new int[0]; }
}
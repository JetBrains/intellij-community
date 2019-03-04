import org.jetbrains.annotations.NotNull;

public class NoCheckForNewMultiArray {
  @NotNull
  Object method() { return new int[0][0]; }
}
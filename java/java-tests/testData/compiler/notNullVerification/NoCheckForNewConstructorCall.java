import org.jetbrains.annotations.NotNull;

public class NoCheckForNewConstructorCall {

  public NoCheckForNewConstructorCall(int p1, String p2) {
  }

  @NotNull
  Object method() { return new NoCheckForNewConstructorCall(42, "42"); }
}
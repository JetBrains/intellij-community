import org.jetbrains.annotations.NotNull;

public class NoCheckForPrivateNotNullMethodCall {
  @NotNull
  private String foo() {return "a";}

  @NotNull
  Object method() { return foo(); }
}
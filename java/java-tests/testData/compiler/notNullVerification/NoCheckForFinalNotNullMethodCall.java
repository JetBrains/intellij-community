import org.jetbrains.annotations.NotNull;

public class NoCheckForFinalNotNullMethodCall {
  @NotNull
  final String foo() {return "a";}

  @NotNull
  Object method() { return foo(); }
}
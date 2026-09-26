import org.jetbrains.annotations.NotNull;

public class NoCheckForStaticNotNullMethodCall {
  @NotNull
  static String foo() {return "a";}

  @NotNull
  Object method() { return foo(); }
}
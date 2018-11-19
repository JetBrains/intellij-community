import org.jetbrains.annotations.*;

public class WrongParameter {
  public boolean resolveAction(@NotNull Object action, @NotNull Object combatant, @NotNull Object userInputProvider) {
    return false;
  }

  public void foo() {
    resolveAction(
      new Object(),
      new Object(),
      <warning descr="Passing 'null' argument to parameter annotated as @NotNull">null</warning>); // Last parameter should be highlighted
  }
}
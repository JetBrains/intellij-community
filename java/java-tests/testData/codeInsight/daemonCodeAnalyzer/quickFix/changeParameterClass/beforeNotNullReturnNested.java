// "Make 'A' implement 'J'" "true-preview"
import org.jetbrains.annotations.NotNull;

interface J<S> {}
interface I {}

class A implements I {
  public @NotNull J<@NotNull String> foo() {
    return <caret>this;
  }
}

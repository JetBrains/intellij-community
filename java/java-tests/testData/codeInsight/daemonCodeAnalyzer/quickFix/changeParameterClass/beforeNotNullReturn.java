// "Make 'A' implement 'J'" "true-preview"
import org.jetbrains.annotations.NotNull;

interface J {}
interface I {}

class A implements I {
  public @NotNull J foo() {
    return <caret>this;
  }
}

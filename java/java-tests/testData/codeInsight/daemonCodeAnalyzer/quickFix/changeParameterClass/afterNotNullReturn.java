// "Make 'A' implement 'J'" "true-preview"
import org.jetbrains.annotations.NotNull;

interface J {}
interface I {}

class A implements I, J <caret>{
  public @NotNull J foo() {
    return this;
  }
}

// "Create method 'newMethod'" "true-preview"
import java.util.Objects;

class X {
  <T extends Serializable & Cloneable> void intersection() {
    T t = Objects.requireNonNull(<caret>newMethod());
  }
}

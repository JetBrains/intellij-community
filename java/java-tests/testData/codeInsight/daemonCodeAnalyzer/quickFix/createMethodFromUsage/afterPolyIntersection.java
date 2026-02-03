// "Create method 'newMethod'" "true-preview"
import java.util.Objects;

class X {
  <T extends Serializable & Cloneable> void intersection() {
    T t = Objects.requireNonNull(newMethod());
  }

    private <T extends Serializable & Cloneable> T newMethod() {
        return null;
    }
}

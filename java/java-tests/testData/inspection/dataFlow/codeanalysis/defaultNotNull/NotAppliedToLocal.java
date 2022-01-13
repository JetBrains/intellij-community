import org.jspecify.nullness.NullMarked;
import org.jspecify.nullness.Nullable;

@NullMarked
class NullnessDemo {
  @Nullable Object something() {
    return null;
  }

  void foo() {
    Object o = something();
  }
}

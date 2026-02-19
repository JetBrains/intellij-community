import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class NullnessDemo {
  @Nullable Object something() {
    return null;
  }

  void foo() {
    Object o = something();
  }
}

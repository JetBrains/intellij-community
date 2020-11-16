import org.jspecify.annotations.DefaultNonNull;
import org.jspecify.annotations.Nullable;

@DefaultNonNull
class NullnessDemo {
  @Nullable Object something() {
    return null;
  }

  void foo() {
    Object o = something();
  }
}

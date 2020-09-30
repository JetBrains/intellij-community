import jspecify.annotations.DefaultNonNull;
import jspecify.annotations.Nullable;

@DefaultNonNull
class NullnessDemo {
  @Nullable Object something() {
    return null;
  }

  void foo() {
    Object o = something();
  }
}

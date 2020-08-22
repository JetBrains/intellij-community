import jspecify.annotations.DefaultNonNull;
import jspecify.annotations.Nullable;

@DefaultNonNull
class NullnessDemo {
  static <E extends @Nullable Object> void foo(E e) {
  }
  
  void call() {
    foo(null);
  }
}

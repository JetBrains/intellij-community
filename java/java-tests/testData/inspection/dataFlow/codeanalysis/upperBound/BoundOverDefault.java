import org.jspecify.nullness.NullMarked;
import org.jspecify.nullness.Nullable;

@NullMarked
class NullnessDemo {
  static <E extends @Nullable Object> void foo(E e) {
  }
  
  void call() {
    foo(null);
  }
}

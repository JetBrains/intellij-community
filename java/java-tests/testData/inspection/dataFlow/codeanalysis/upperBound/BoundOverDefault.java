import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class NullnessDemo {
  static <E extends @Nullable Object> void foo(E e) {
  }
  
  void call() {
    foo(null);
  }
}

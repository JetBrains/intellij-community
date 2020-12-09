import org.jspecify.annotations.DefaultNonNull;
import org.jspecify.annotations.Nullable;

@DefaultNonNull
class NullnessDemo {
  static <E extends @Nullable Object> void foo(E e) {
  }
  
  void call() {
    foo(null);
  }
}

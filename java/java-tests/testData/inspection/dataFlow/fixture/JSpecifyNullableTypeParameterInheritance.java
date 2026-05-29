import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.Nullable;

@NullMarked
class Scratch<A extends @Nullable Object> {
  <F extends A> void foo(F foo) {
    if (foo != null) {
    }
  }
}
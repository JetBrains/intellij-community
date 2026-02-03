// "Annotate container as '@NullMarked'|->Remove '@NullUnmarked' from class 'Enclosing'" "true"

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;

class NonNullHolder<T extends @org.jspecify.annotations.NonNull Object> {}

@NullUnmarked
@NullMarked
class Enclosing {
  public void foo(NonNullHolder<<caret>String> holder) {
  }
}

import org.jspecify.annotations.NullMarked;
import org.jspecify.annotations.NullUnmarked;
import org.jspecify.annotations.Nullable;

import java.util.List;

@NullMarked
class Container<T extends @Nullable Object> {

  @NullUnmarked
  List<T> get() {
    return <warning descr="'null' is returned by the method which is not declared as @Nullable">null</warning>;
  }
}
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.NotNullByDefault;
import org.jetbrains.annotations.Nullable;

import java.util.List;

@NotNullByDefault
class Container<T> {


  <L extends @Nullable Object> List<@NotNull L> get() {
    return null;
  }

  void call() {
    for (Object t : get()) {
      System.out.println(t.toString());
    }
  }
}
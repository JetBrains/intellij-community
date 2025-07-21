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

  <L extends String> List<<warning descr="Redundant nullability annotation: type parameter upper bound is already non-null">@NotNull</warning> L> get2() {
    return null;
  }
}

class NoContainer {
  <L extends @Nullable Object> List<@NotNull L> get() {
    return null;
  }
  <L extends String> List<@NotNull L> get2() {
    return null;
  }
  <L extends @NotNull String> List<<warning descr="Redundant nullability annotation: type parameter upper bound is already non-null">@NotNull</warning> L> get3() {
    return null;
  }
  
}
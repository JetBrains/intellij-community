import org.jetbrains.annotations.NotNull;

import java.util.List;

class A {

  interface FI<T> {
    @NotNull List<T> getX(@NotNull T value);
  }

  void foo() {
    FI<String> f = value -> List.of(value);
    FI<String> f2 = List::of;
  }
}
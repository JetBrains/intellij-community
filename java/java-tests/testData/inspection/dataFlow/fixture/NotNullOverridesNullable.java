import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.lang.Object;
import java.lang.Override;

class Super {
  @Nullable Object foo() {
    return null;
  }
}

class Main extends Super {
  @NotNull
  @Override
  Object foo() {
    return 2;
  }

  void bar(@NotNull Object o) {}

  void goo() {
    bar(foo());
  }

}
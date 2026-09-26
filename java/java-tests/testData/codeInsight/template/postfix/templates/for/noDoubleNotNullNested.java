import org.jetbrains.annotations.NotNull;

import java.util.List;

interface Parent<T> {
  @NotNull List<@NotNull List<@NotNull T>> getList();
}

interface Child extends Parent<@NotNull String> {
}

class JavaMain {
  void test(Child child) {
    child.getList().for<caret>
  }
}
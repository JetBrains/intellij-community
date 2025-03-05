import org.jetbrains.annotations.NotNull;
import java.lang.annotation.ElementType;
import java.lang.annotation.Target;

import java.util.List;

@Target({ElementType.TYPE_USE})
@interface Anno {}

interface Parent<T> {
  @NotNull List<@Anno @NotNull T> getList();
}

interface Child extends Parent<@NotNull String> {
}

class JavaMain {
  void test(Child child) {
    child.getList().for<caret>
  }
}
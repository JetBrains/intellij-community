// "Insert 'filter(Objects::nonNull)' step" "false"
import org.jetbrains.annotations.Nullable;

import java.util.List;

class MyClass {
  interface MyFunction {
    String apply(@Nullable String s);
  }

  void doSmth(MyFunction fn) {}

  void test(List<String> list, @Nullable String s1) {
    this.doSmth(s -> s.t<caret>rim());
  }
}


// "Insert 'filter(Objects::nonNull)' step" "true-preview"
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;

class MyClass {
  @Nullable String convert(String s) {
    return s;
  }

  String accept(@NotNull String s) {
    return s;
  }

  void test(List<String> list, @Nullable String s1) {
    list.stream().map(this::convert).map(this:<caret>:accept).forEach(System.out::println);
  }
}


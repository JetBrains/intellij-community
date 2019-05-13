// "Insert 'filter(Objects::nonNull)' step" "true"
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.Objects;

class MyClass {
  @Nullable String convert(String s) {
    return s;
  }

  String accept(@NotNull String s) {
    return s;
  }

  void test(List<String> list, @Nullable String s1) {
    list.stream().map(this::convert).filter(Objects::nonNull).map(s -> accept(s)).forEach(System.out::println);
  }
}


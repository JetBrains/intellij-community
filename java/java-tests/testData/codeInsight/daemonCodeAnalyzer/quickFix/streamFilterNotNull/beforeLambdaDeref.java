// "Insert 'filter(Objects::nonNull)' step" "true"
import org.jetbrains.annotations.Nullable;

import java.util.List;

class MyClass {
  @Nullable String convert(String s) {
    return s;
  }

  void test(List<String> list, @Nullable String s1) {
    list.stream().map(this::convert).map(s -> s.t<caret>rim()).forEach(System.out::println);
  }
}


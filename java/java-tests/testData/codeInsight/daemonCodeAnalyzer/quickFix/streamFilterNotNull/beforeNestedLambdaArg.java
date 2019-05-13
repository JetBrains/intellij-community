// "Insert 'filter(Objects::nonNull)' step" "true"
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.List;
import java.util.function.Supplier;

class MyClass {
  @Nullable String convert(String s) {
    return s;
  }

  String accept(@NotNull String s) {
    return s;
  }

  String execute(Supplier<String> op) {
    return op.get();
  }

  void test(List<String> list, @Nullable String s1) {
    list.stream().map(this::convert).map(s -> execute(() -> accept(s<caret>))).forEach(System.out::println);
  }
}


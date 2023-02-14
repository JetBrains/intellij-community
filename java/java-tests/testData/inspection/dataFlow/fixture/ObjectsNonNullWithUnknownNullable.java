import java.util.*;
import java.util.function.*;
import typeUse.*;

public class ObjectsNonNullWithUnknownNullable {
  void foo(@NotNull List<@NotNull String> list) {
    Predicate<String> predicate = Objects::nonNull;
    list.stream()
      .map(s -> s.isEmpty() ? null : s)
      .filter(Objects::nonNull)
      .forEach(System.out::println);
  }
}
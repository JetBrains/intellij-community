import foo.NotNull;
import foo.Nullable;

import java.util.*;
import java.util.function.*;
import java.util.stream.*;

public class StreamTypeAnnoInlining {
  void testToArray() {
    @NotNull Object @NotNull[] foo0 = Stream.of("a", "b")
                                            .map(x-> <warning descr="Function may return null, but it's not allowed here">"a".equals(x) ? null : x.toUpperCase()</warning>)
                                           .toArray();

    // IDEA-194697
    @NotNull String @NotNull[] foo = Stream.of("a", "b")
                                           .map(x-> <warning descr="Function may return null, but it's not allowed here">"a".equals(x) ? null : x.toUpperCase()</warning>)
                                           .toArray(String[]::new);

    @NotNull String @NotNull[] foo1 = Stream.of("b", "c")
                                           .map(x-> <warning descr="Condition '\"a\".equals(x)' is always 'false'">"a".equals(x)</warning> ? null : x.toUpperCase())
                                           .toArray(String[]::new);

    @NotNull String @NotNull[] foo2 = Stream.of("a", "b")
                                           .map(x-> "a".equals(x) ? null : x.toUpperCase())
                                           .filter(Objects::nonNull)
                                           .toArray(String[]::new);
  }

  void testFromList(List<@NotNull String> list) {
    list.stream().filter(<warning descr="Method reference result is always 'true'">Objects::nonNull</warning>).forEach(System.out::println);
  }

  List<@NotNull String> testFromNullableToNotNull(List<@Nullable String> list) {
    return list.stream().map(x -> <warning descr="Function may return null, but it's not allowed here">x</warning>).collect(Collectors.toList());
  }

  List<@Nullable String> testFromNullableToNullable(List<@Nullable String> list) {
    return list.stream().map(x -> x).collect(Collectors.toList());
  }

  List<@NotNull String> testFromUnknownToNotNull(List<String> list) {
    return list.stream().map(x -> x).collect(Collectors.toList());
  }
}

import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.stream.Stream;

class Main {
  public static void main(String[] args) {
    Stream.of("test1")
      .map(Main::testNullableStaticMethod)
      .map(Objects::requireNonNull);

    Stream.of("test2")
      .map(Main::testNullableStaticMethod)
      .map(obj -> Objects.requireNonNull(obj));

    Stream.of("test3")
      .map(Main::testNullableStaticMethod)
      .map(<warning descr="Method reference argument might be null but passed to non-annotated parameter">Main::requireNonNull</warning>);
  }

  static <T> T requireNonNull(T obj) {
    if (obj == null) {
      throw new NullPointerException();
    }
    return obj;
  }

  @Nullable
  public static native String testNullableStaticMethod(String something);
}
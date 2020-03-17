import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.Nullable;

class Foo {
  @Contract(pure = true, value = "null -> fail; true -> true; false -> false")
  private static boolean nonNullBoolean(@Nullable Boolean value) {
    if (value == null) throw new IllegalArgumentException();
    return value;
  }
}

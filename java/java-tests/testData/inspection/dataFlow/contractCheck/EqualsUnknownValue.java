import org.jetbrains.annotations.Contract;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

abstract class Foo {
  public static final String CONSTANT = getSomeString();

  static native String getSomeString();

  @Contract("null -> false")
  static boolean isConstant(@Nullable String s) {
    return s == CONSTANT;
  }

  @Contract("null -> false")
  static boolean isSomeString(@Nullable String s) {
    return s == getSomeString();
  }

  @Contract("null,_ -> false")
  static boolean isParameter(@Nullable String s, String param) {
    return s == param;
  }

}

// "Cast expression to 'java.lang.String'" "false"
import java.util.function.Predicate;
import java.util.function.Supplier;

class X {
  public static String getOrDefault(String prefer, Supplier<String> def) {
    return getOrDefault(() -> prefer, String::isEmpty, def<caret>.get());
  }

  public static <T> T getOrDefault(Supplier<T> prefer, Predicate<T> abandon, Supplier<T> def) {
    return null;
  }
}
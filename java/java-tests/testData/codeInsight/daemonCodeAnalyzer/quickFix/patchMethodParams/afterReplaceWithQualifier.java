// "Replace 3rd argument with qualifier" "true-preview"
import java.util.function.Predicate;
import java.util.function.Supplier;

class X {
  public static String getOrDefault(String prefer, Supplier<String> def) {
    return getOrDefault(() -> prefer, String::isEmpty, def);
  }

  public static <T> T getOrDefault(Supplier<T> prefer, Predicate<T> abandon, Supplier<T> def) {
    return null;
  }
}
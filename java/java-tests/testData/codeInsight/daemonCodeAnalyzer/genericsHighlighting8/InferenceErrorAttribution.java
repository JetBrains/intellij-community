import java.util.function.Predicate;
import java.util.function.Supplier;

class X {
  public static String getOrDefault(String prefer, Supplier<String> def) {
    return getOrDefault<error descr="'getOrDefault(java.util.function.Supplier<T>, java.util.function.Predicate<T>, java.util.function.Supplier<T>)' in 'X' cannot be applied to '(<lambda expression>, <method reference>, java.lang.String)'">(() -> prefer, String::isEmpty, def.get())</error>;
  }

  public static <T> T getOrDefault(Supplier<T> prefer, Predicate<T> abandon, Supplier<T> def) {
    return null;
  }
}
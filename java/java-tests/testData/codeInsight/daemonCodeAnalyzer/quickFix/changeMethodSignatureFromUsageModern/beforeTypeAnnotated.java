// "<html> Change signature of tryCast(Class&lt;Integer&gt;)</html>" "false"
import java.lang.annotation.*;
class F {
  public static <T> T tryCast(@NotNull Class<T> clazz) {
    return null;
  }

  void test(Object obj) {
    String s = try<caret>Cast(Integer.class);
  }
}
@Target({ ElementType.PARAMETER, ElementType.TYPE_USE})
public @interface NotNull {}
// "Remove explicit type arguments" "false"
import java.util.List;

class Collectors {
  public static <T> void foo(List<T> list) {}
  {
    foo(Collectors.<Str<caret>ing>of());
  }

  public static <E> List<E> of() {
    return null;
  }
}
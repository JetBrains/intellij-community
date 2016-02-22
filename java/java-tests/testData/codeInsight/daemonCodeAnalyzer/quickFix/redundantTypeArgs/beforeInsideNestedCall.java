// "Remove explicit type arguments" "true"
import java.util.List;

class Collectors {
  public static void foo(List<String> list) {}
  {
    foo(Collectors.<Str<caret>ing>of());
  }

  public static <E> List<E> of() {
    return null;
  }
}
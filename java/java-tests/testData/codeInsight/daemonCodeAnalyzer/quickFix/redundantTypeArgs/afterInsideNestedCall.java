// "Remove type arguments" "true"
import java.util.List;

class Collectors {
  public static void foo(List<String> list) {}
  {
    foo(Collectors.of());
  }

  public static <E> List<E> of() {
    return null;
  }
}
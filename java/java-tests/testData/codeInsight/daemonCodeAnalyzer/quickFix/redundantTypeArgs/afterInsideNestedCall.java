// "Remove type arguments" "true-preview"
import java.util.List;

class Collectors {
  public static void foo(List<String> list) {}
  {
      //c1
      foo(Collectors.of());
  }

  public static <E> List<E> of() {
    return null;
  }
}
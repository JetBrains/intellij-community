// "Remove type arguments" "true"
import java.util.List;

class Collectors {
  {
    List<String> l = Collectors.of();
  }

  public static <E> List<E> of() {
    return null;
  }
}
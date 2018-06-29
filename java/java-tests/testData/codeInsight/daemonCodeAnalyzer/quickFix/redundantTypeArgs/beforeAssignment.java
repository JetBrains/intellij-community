// "Remove type arguments" "true"
import java.util.List;

class Collectors {
  {
    List<String> l = Collectors.<Str<caret>ing>of();
  }

  public static <E> List<E> of() {
    return null;
  }
}
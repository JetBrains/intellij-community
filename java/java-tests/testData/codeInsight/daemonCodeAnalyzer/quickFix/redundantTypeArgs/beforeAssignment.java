// "Remove type arguments" "true-preview"
import java.util.List;

class Collectors {
  {
    List<String> l = Collectors.<Str<caret>ing>of();
  }

  public static <E> List<E> of() {
    return null;
  }
}
import java.util.List;

public class ConditionalWithListOfAsArgument {
  static <T> Iterable<T> id(Iterable<? extends T> it) { return null; }

  void a(Iterable<String> src, boolean b) {
    for (String s : id(b ? src : List.of())) { }
    for (String s : id(b ? List.of() : src)) { }
    Iterable<String> r = id(b ? src : List.of());
  }
}

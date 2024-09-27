import org.jetbrains.annotations.*;
import java.util.List;

@NotNullByDefault
public class JetBrainsNotNullByDefault {
  String field;

  String test(String param) {
    if (<warning descr="Condition 'param == null' is always 'false'">param == null</warning>) {}
    if (<warning descr="Condition 'field == null' is always 'false'">field == null</warning>) {}
    String local = System.getProperty("a");
    if (local == null) {}
    return <warning descr="'null' is returned by the method declared as @NotNullByDefault">null</warning>;
  }

  <T> T generic(T param) {
    if (param == null) {
      return <warning descr="'null' is returned by the method which is not declared as @Nullable">param</warning>;
    }
    return <warning descr="'null' is returned by the method which is not declared as @Nullable">null</warning>;
  }

  <T> List<T> genericList(List<T> param) {
    for (T t : param) {
      if (t == null) {
        return <warning descr="'null' is returned by the method declared as @NotNullByDefault">null</warning>;
      }
    }
    return param;
  }

  void use2(String s) {
    // T is inferred as String from "hello" type
    if (generic("hello") == null) {}
    // T is inferred as @NotNull String from the `s` type
    if (<warning descr="Condition 'generic(s) == null' is always 'false'">generic(s) == null</warning>) {}
  }

  void use(List<String> list) {
    // T is inferred as @NotNull String from the `list` type
    for (String s : genericList(list)) {
      if (<warning descr="Condition 's == null' is always 'false'">s == null</warning>) {}
    }
  }

  static class StaticInner implements NullableMember {
    public String myGet() {
      return <warning descr="'null' is returned by the method declared as @NotNullByDefault">null</warning>;
    }

    @Override
    public String get() {
      return null;
    }
  }
}

interface NullableMember {
  @Nullable String get();
}
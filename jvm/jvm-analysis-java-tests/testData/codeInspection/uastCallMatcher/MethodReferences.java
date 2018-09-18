import java.util.List;
import java.util.function.Function;

public class MethodReferences {
  public void foo(List<String> list) {
    list.stream().map(String::toUpperCase);
    list.stream().map(String::chars);
    list.stream().map(java.util.Objects::hashCode); // static method

    list.stream().map(this::bar);
    Function<String, String> f = null;
    list.stream().map(f::apply);
  }

  private String bar(String s) {
    return null;
  }
}
import java.util.List;

class A {
  public static <T> List<T> asList(T... a) { }

  List<String> m() {
    return asList(new <caret>);
  }
}
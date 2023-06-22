import java.util.List;

public class Level {

  record Generic<T>(T t) {

  }

  public static void test(List<Generic<Generic<Generic<String>>>> list) {
    for (Generic(var t<caret>) : list) {
      System.out.println(t.t);
    }
  }

  public static void test2(List<Generic<Generic<Generic<String>>>> list) {
    for (Generic(Generic(var t)) : list) {
      System.out.println(t.t);
    }
  }
}

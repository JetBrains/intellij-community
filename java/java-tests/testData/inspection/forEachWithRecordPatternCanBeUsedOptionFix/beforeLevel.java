// "Fix all 'Enhanced 'for' with a record pattern can be used' problems in file" "true"
import java.util.List;

public class Level {

  record Generic<T>(T t) {

  }

  public static void test(List<Generic<Generic<Generic<String>>>> list) {
    for (Generic(var t) : list) {
      System.out.println(t.t<caret>);
    }
  }

  public static void test2(List<Generic<Generic<Generic<String>>>> list) {
    for (Generic(Generic(var t)) : list) {
      System.out.println(t.t);
    }
  }
}

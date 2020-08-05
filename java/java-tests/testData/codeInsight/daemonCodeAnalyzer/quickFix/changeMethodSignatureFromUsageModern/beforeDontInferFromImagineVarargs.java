// "Add 'String' as 2nd parameter to method 'get'" "true"
import java.util.List;
class Test<T> {

  public LazyVal(final List<T> ts) {
    get(t<caret>s, "");
  }
  public static <T1> void get(List<T1> l) {}
}
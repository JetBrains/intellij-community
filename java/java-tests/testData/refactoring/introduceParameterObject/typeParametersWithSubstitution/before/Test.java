import java.util.Collection;
import java.util.List;

public class Test {
  public static <T> String foo(List<T> y) {
    return null;
  }

  void bar(List<Integer> list) {
    System.out.println(foo(list));
  }
}

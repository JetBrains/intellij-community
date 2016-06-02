import java.util.List;

public class Test {
  public static <T> String foo(Param<T> param) {
    return null;
  }

  void bar(List<Integer> list) {
    System.out.println(foo(new Param<>(list)));
  }
}

import java.util.List;

class Test {
  void a(List<String> range1, List<String> range2) {
    zip(range1, range2, Test::<String>asList);
    I1<String> i = Test :: asList;
  }

  public static <A, B, C> List<C> zip(List<? extends A> a,
                                      List<? extends B> b,
                                      BiFunction<? super A, ? super B, ? extends C> zipper) {
    return null;
  }

  public interface BiFunction<T, U, R> {
    R apply(T t, U u);
  }

  public static <T> List<T> asList(T... a) {
    return null;
  }

  interface I1<T> {
    List<T> a(T... t);
  }
}
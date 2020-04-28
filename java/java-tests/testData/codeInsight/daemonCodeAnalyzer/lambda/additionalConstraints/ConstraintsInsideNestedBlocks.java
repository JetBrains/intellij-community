
import java.util.List;
import java.util.function.Function;

class MyTest<O> {
  private void test(MyTest<List<String>> listMono) {
    expand(id -> listMono.flatMap(l -> {
      if (l.size() == 0) {
        return null;
      }
      else {
        return MyTest.empty();
      }
    }));
  }
  void expand(Function<? super List<String>, ? extends MyTest<? extends List<String>>> expander) {

  }

  public final <R> MyTest<R> flatMap(Function<? super List<String>, ? extends MyTest<? extends R>> transformer) {
    return null;
  }


  public static <T> MyTest<T> empty() {
    return null;
  }
}

import java.util.List;
class MyTest {
  void m(List<? extends Bar> bars) {
    n(bars.get(0));
    bars.forEach(b -> n(b));
    bars.forEach(MyTest::n);
  }
  private static <T> void n(Bar<T> bar) { }
  private static class Bar<T> { }
}

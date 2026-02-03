import java.util.function.Predicate;
class X<T> {
  void test(Predicate<String> p) {
  }
  void foo(X x) {
    x.test((Predicate<String>)e -> e.isEmpty());
  }
}
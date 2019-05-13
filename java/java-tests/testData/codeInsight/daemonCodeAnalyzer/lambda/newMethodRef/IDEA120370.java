import java.util.List;
import java.util.concurrent.Callable;

class Tmp {
  static void doo(Runnable action){}
  static void doo(Callable<?> action){}

  interface X<T> {
    void bar();
  }

  interface Y {
    void bar();
  }

  void test(X<String> x1, X x2, Y y) {
    doo(x1::bar);

    doo(x2::bar);

    doo(y::bar);
  }
}
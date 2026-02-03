import org.jspecify.annotations.*;

class X {
  int f;

  void test(X x) {
    x.f = 1;
  }
}

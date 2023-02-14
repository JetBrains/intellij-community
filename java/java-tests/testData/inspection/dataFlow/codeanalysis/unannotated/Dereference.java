import org.jspecify.nullness.*;

class X {
  int f;

  void test(X x) {
    x.f = 1;
  }
}

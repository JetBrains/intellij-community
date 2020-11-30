import org.jspecify.annotations.*;

class X {
  int f;

  void test(@Nullable X x) {
    /*ca-nullable-to-not-null*/x.f = 1;
  }
}

import org.jspecify.nullness.*;

class X {
  int f;

  void test(@Nullable X x) {
    m(/*ca-nullable-to-not-null*/x);
  }

  native void m(@NonNull X x);
}
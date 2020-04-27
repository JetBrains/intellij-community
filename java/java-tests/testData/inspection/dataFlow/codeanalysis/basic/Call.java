import codeanalysis.experimental.annotations.*;

class X {
  int f;

  void test(@Nullable X x) {
    m(/*ca-nullable-to-not-null*/x);
  }

  native void m(@NotNull X x);
}
class Test {
  int m(@org.jetbrains.annotations.Nullable Integer i) {
    Bx<Integer> f = () -> {
      return i;
    };
    Bx<Integer> f1 = () -> i;
    return 1;
  }

  void n(@org.jetbrains.annotations.Nullable Integer i) {
    Unbx f  = () -> <warning descr="Unboxing of 'i' may produce 'java.lang.NullPointerException'">i</warning>;
    Unbx f1 = () -> {
      return <warning descr="Unboxing of 'i' may produce 'java.lang.NullPointerException'">i</warning>;
    };
  }

  interface Unbx {
    int m();
  }

  interface Bx<T> {
    @org.jetbrains.annotations.Nullable
    T m();
  }
}


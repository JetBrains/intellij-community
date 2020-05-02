import org.jetbrains.annotations.Nullable;

class X<T> {
  static void test2(X<Double> d, boolean flag) {
    Double val = flag ? 1.0 : <warning descr="Unboxing of 'd.get()' may produce 'NullPointerException'">d.get()</warning>;
    System.out.println(val);
  }

  static void test(boolean flag) {
    Double val = flag ? 1.0 : get(1.0);
    System.out.println(val);
  }

  @Nullable
  static <T> T get(T foo) {
    return null;
  }

  @Nullable T get() {
    return null;
  }

  public static void main(String[] args) {
    test(false);
    test2(new X<>(), false);
  }
}
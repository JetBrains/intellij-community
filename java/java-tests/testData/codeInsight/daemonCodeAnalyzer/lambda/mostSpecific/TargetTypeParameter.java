import java.util.function.Supplier;

class Test {

  public static void main(String... args) {
    <error descr="Ambiguous method call: both 'Test.c(Supplier<Integer>, Supplier<Integer>)' and 'Test.c(Supplier<T>, T)' match">c</error>(() -> 3, () -> 10);
  }

  public static <T> void c(Supplier<T> s1, Supplier<T> s2) {}
  public static <T> void c(Supplier<T> s1, T value) {}
}

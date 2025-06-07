import java.util.function.Supplier;

class Test {

  public static void main(String... args) {
    c<error descr="Ambiguous method call: both 'Test.c(Supplier<Integer>, Supplier<Integer>)' and 'Test.c(Supplier<Integer>, Integer)' match">(() -> 3, () -> 10)</error>;
  }

  public static <T> void c(Supplier<T> s1, Supplier<T> s2) {}
  public static <T> void c(Supplier<T> s1, T value) {}
}

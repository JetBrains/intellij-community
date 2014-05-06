import java.util.function.Supplier;

class Test {

  public static void main(String... args) {
    c<error descr="Cannot resolve method 'c(<lambda expression>, <lambda expression>)'">(() -> 3, () -> 10)</error>;
  }

  public static <T> void c(Supplier<T> s1, Supplier<T> s2) {}
  public static <T> void c(Supplier<T> s1, T value) {}
}

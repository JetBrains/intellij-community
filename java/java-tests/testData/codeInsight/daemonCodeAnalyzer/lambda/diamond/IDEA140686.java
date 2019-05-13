package nise;

import java.util.function.BiFunction;
import java.util.function.Predicate;
import java.util.function.Supplier;



class TypeTest {
  public static void main(String[] args) {
    assertThat(id(new Combiner<>(echo("A"), echo("B"), (str1, str2) ->
                 new StringBuilder(str1.length() + str2.length())
                   .append(str1)
                   .append(str2))).get(),
               hasSameContentAs("AB"));
  }

  private static <R> Supplier<R> id(Supplier<R> s) {
    return s;
  }

  private static <T> Supplier<T> echo(T s) {
    return () -> s;
  }

  private static Predicate<CharSequence> hasSameContentAs(CharSequence seq) {
    return charSequence -> charSequence.toString().equals(seq.toString());
  }

  private static <T> void assertThat(T actual, Predicate<? super T> matcher) {
    if (!matcher.test(actual)) throw new AssertionError();
  }

  private static class Combiner<T1, T2, R> implements Supplier<R> {
    private final Supplier<T1> s1;
    private final Supplier<T2> s2;
    private final BiFunction<T1, T2, R> f;

    public Combiner(Supplier<T1> s1, Supplier<T2> s2, BiFunction<T1, T2, R> f) {
      this.s1 = s1;
      this.s2 = s2;
      this.f = f;
    }

    @Override
    public R get() {
      return f.apply(s1.get(), s2.get());
    }
  }
}
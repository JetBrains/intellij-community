import java.util.Collection;


class UncheckedBug
{

  void foo(Collection<String> strings) {
    assertThat(strings, hasSize(0));
  }

  public static <E> Matcher<Collection<? extends E>> hasSize(int <warning descr="Parameter 'size' is never used">size</warning>) {
    return null;
  }

  public static <T> void assertThat(T <warning descr="Parameter 'actual' is never used">actual</warning>, Matcher<? super T> <warning descr="Parameter 'matcher' is never used">matcher</warning>) {
  }

  interface Matcher<<warning descr="Type parameter 'T' is never used">T</warning>> {}
}
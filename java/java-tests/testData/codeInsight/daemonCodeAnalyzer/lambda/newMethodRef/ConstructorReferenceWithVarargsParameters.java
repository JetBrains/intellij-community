import java.util.function.Function;

class Foo<H> {

  private <S> Foo(Foo<S> s, Foo... ss) {}

  {
    foo(Foo::new);
  }

  private <T> void foo(final Function<Foo, T> param) { }
}


import java.util.function.Function;

class Foo<H> {

    private Foo(Foo s, Foo... ss) {}

    {
         foo(Foo::new);
    }

    private <T> void foo(final Function<Foo, T> param) { }
}

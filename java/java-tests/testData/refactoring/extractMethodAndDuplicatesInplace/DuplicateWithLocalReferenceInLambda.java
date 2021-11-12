import java.util.function.Consumer;

public class Foo {
    void f(Foo f ) {
        <selection>f(c -> c.getFoo());</selection>
        f(c -> c.getFoo());
    }

    void f(Consumer<? extends Foo> b){}

    Foo getFoo() {
      return this;
    }
}

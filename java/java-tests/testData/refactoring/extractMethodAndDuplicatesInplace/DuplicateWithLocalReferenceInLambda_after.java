import java.util.function.Consumer;

public class Foo {
    void f(Foo f ) {
        extracted();
        extracted();
    }

    private void extracted() {
        f(c -> c.getFoo());
    }

    void f(Consumer<? extends Foo> b){}

    Foo getFoo() {
      return this;
    }
}

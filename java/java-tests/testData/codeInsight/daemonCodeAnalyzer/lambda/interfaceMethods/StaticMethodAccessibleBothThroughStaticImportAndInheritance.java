import p.*;
import static p.Foo.bar;
import static p.Boo.*;

class FooImpl implements Foo, Boo {
    public void baz() {
        <error descr="Static method may only be called on its containing interface">foo</error>();
        bar();
        boo();
    }
}
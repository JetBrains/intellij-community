import p.*;
import static p.Foo.bar;
import static p.Boo.*;

class FooImpl implements Foo, Boo {
    public void baz() {
        <error descr="Static method may be invoked on containing interface class only">foo();</error>
        bar();
        boo();
    }
}
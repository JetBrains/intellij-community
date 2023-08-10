import static p.Foo.FooEx.<error descr="Static method may be invoked on containing interface class only">foo</error>;

class FooImpl {
    public void baz() {
        <error descr="Static method may be invoked on containing interface class only">foo();</error>
    }
}
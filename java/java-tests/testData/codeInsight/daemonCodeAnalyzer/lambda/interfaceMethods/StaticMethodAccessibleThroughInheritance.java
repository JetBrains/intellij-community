import static <error descr="Static method may only be called on its containing interface">p.Foo.FooEx.foo;</error>

class FooImpl {
    public void baz() {
        <error descr="Static method may only be called on its containing interface">foo</error>();
    }
}
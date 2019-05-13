interface Foo<A> {}
interface Bar extends Foo<Boolean> {}
class FooFactory {
    public <A> Foo<A> getFoo() {
        return (Foo<A>) new Bar() {};
    }
}
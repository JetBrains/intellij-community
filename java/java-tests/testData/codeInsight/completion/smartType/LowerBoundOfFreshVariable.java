
interface Foo<T extends Foo<T>> {

    static <F extends Foo<F>> void foo(F f) {
        bar(<caret>);
    }

    static <B extends Foo<B>> void bar(B b) { }
}
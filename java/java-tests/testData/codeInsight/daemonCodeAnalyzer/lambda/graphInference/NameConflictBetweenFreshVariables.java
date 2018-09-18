class MyTest {

    static class Foo<A> {}

    static <B> void bar(Foo<? super B> f1, Foo<? super B> f2) { }

    {
        bar<error descr="'bar(MyTest.Foo<? super B>, MyTest.Foo<? super B>)' in 'MyTest' cannot be applied to '(MyTest.Foo<T>, MyTest.Foo<T>)'">(m(), m())</error>;
    }

    static <T extends Comparable<T>> Foo<T> m() {
        return null;
    }
}
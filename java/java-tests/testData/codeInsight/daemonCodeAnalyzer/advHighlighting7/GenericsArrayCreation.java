class A<T> {
    class B{}
}
class C<T> extends A<T> {
    {
        B[] o = <error descr="Generic array creation">{}</error>;
    }
}
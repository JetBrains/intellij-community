abstract class C<T> {
    abstract void foo(T t);
}

class B<T> extends C<T> {
    @Override
    void f<caret>oo(T t) {

    }
}
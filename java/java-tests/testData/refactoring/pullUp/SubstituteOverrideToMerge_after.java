abstract class C<T> {
    abstract void foo(T t);

    @Override
    void foo(T t) {

    }
}

class B<T> extends C<T> {
}
class A<E> {
}

class B<T> extends A<T> {
    <T2 extends T> void foo(T2 t) {
    }
}
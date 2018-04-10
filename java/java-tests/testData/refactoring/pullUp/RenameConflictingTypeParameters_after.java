class A<E> {
    <E2 extends E> void foo(E2 t) {
    }
}

class B<T> extends A<T> {
}
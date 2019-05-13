abstract class A1<X>{
    abstract <T> void foo(T t, X x);
}

class B1<T> extends A1<T>{
    @Override
    <T1> void foo(T1 t1, T t) {
        <caret>
    }
}

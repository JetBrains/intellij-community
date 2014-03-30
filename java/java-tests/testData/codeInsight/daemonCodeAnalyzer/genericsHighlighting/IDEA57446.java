abstract class A<T>{
    abstract void foo(T x);
    class B extends A<B> {
        @Override
        void foo(A<T>.B x) {}
    }
}
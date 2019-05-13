// "Replace 'var' with explicit type" "false"
final class Example<J, T>  {

    interface I<A, B> {
        void m(A a, B b);
    }
    
    void m(I<T, J> i) {}
    
    void m(Example<? super String, Integer> e) {
        e.m((v<caret>ar a, var b) -> {});
    }
}

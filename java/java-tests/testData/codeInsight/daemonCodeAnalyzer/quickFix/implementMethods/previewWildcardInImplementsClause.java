// "Implement methods" "true-preview""
interface I<T> {
    void m(I<? extends T> i, T v);
}
class Impl implements I<? super Number> {
    @Override
    public void m(I<?> i, Object v) {
        
    }
}
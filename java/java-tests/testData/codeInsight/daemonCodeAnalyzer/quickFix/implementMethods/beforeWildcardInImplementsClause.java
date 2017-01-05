// "Implement methods" "true"
interface I<T> {
    void m(I<? extends T> i, T v);
}
class I<caret>mpl implements I<? super Number> {
}
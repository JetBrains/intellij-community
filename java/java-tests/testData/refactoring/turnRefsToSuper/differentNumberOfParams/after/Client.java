interface SuperBar<T> {
    void f();
}
interface Bar<T, S> extends SuperBar<S> {}
class Client {
    void foo(SuperBar<Integer> b) {
        b.f();
    }
}
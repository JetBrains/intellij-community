interface SuperBar<T> {
    void f();
}
interface Bar<T, S> extends SuperBar<S> {}
class Client {
    void foo(Bar<String, Integer> b) {
        b.f();
    }
}
public class Test {
    Base<String> b;

    class Base<T> {

    }

    class A<T> extends Base<T> {
        T value;
    }

    class B<T> extends A<T> {}

    void m() {
        T val = b.value;
    }
}
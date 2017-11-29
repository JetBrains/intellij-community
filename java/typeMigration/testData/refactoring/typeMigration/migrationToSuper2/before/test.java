public class Test {
    B<Long> b;

    class Base<T> {

    }

    class A<T> extends Base<T> {
        T value;
    }

    class B<T> extends A<T> {}

    void m() {
        Long val = b.value;
    }
}
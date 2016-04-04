public class Test {
    B<Long> b;

    class Base {

    }

    class A<T> extends Base {
        T value;
    }

    class B<T> extends A<T> {}

    void m() {
        Long val = b.value;
    }
}
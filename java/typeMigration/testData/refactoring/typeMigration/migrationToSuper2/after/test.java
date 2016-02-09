public class Test {
    Base<String> b;

    class Base {

    }

    class A<T> extends Base {
        T value;
    }

    class B<T> extends A<T> {}

    void m() {
        T val = b.value;
    }
}
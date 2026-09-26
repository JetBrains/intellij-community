import java.util.List;

public class InlineSingleImplementation {
    interface MyIface<T> {
        <M> M myGenericMethod(M m, T t);
    }

    static class MyIfaceImpl<E extends CharSequence> implements MyIface<E> {
        @Override
        public <M1> M1 myGenericMethod(M1 m, E e) {
            M1 m1 = m;
            E e1 = e;
            if (m == null) return null;
            System.out.println("Impl: " + m1 + " : " + e);
            return m;
        }
    }

    void test(MyIface<String> iface) {
        int x = iface.<caret>myGenericMethod(123, "hello");
    }
}
import java.util.List;

public class InlineSingleImplementation {
    interface MyIface<T> {
        void myUseGenericMethod(T t);
    }

    static class MyIfaceImpl<E extends CharSequence> implements MyIface<E> {
        @Override
        public void myUseGenericMethod(E e) {
            E e1 = e;
            System.out.println("Impl: " + e);
        }
    }

    void test(MyIface<String> iface) {
        iface.<caret>myUseGenericMethod("hello");
    }
}
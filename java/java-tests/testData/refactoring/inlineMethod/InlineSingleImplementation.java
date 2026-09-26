import java.util.List;

public class InlineSingleImplementation {
    interface MyIface<T> {
        void mySimpleMethod();
    }

    static class MyIfaceImpl<E extends CharSequence> implements MyIface<E> {
        @Override
        public void mySimpleMethod() {
            System.out.println("Impl");
        }
    }

    void test(MyIface<String> iface) {
        iface.<caret>mySimpleMethod();
    }
}
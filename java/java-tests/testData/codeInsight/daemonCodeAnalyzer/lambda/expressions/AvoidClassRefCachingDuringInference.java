import java.util.*;

class Test {
    public static void main(String[] args) {
        new A<>(new B<>(of("")));
    }

    static <E> List<E> of(E element) {
        return null;
    }

    static class A<K> {
        A(String s) {}
        A(B<K> b) {}
    }

    static class B<T> extends ArrayList<List<T>> {
        public B(List<T> l) {}
    }
}
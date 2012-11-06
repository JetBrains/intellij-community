import java.util.*;
class Nav {
    interface Sized {}
    interface Stream<<warning descr="Type parameter 'T' is never used">T</warning>> { }

    private static<U, T extends Sized & Iterable<U>> Stream<U> stream(T entity, int flags) {
        System.out.println(entity);
        System.out.println(flags);
        return null;
    }

    private static<U, T extends Iterable<U>> Stream<U> <warning descr="Private method 'stream(T, int)' is never used">stream</warning>(T entity, int flags) {
        System.out.println(entity);
        System.out.println(flags);
        return null;
    }

    static class A<T> implements Iterable<T>, Sized {
        public Iterator<T> iterator() {
            return null;
        }
    }

    public static void main(String[] args) {
        Stream<String> aStream = stream(new A<String>(), 0);
        System.out.println(aStream);
    }
}
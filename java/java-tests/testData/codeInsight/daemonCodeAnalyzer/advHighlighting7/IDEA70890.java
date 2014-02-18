import java.util.*;

class Test {
        static <E extends Exception, T> void foo(List<T> l, Class<E> ec) throws E {
        }

        public static void main(String[] s) {
                foo(new ArrayList(), RuntimeException.class); //IDEA says we have an unhandled exception here
        }
}
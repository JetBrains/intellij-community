
import java.util.function.Function;


class MyTest {
    {
        Function<B, Try<A>> aNew = Try::new;
        Try<B> bTry = new Try<>(new B());
        Try<A> aTry = bTry.flatMap(Try::new);
    }

    private static class A { }

    private static class B extends A { }

    private static class Try<T> {
        public Try(T t) {
        }
        public Try(Exception e) {
        }

        public <U> Try<U> flatMap(Function<? super T, Try<U>> mapper) {
            return null;
        }
    }

}

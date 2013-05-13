import java.util.Collection;
class Test {
    public static final UnaryFunction<Object, Object, RuntimeException> unaryFunction = new UnaryFunction<Object, Object, RuntimeException>() {
        public Object execute(Object o) throws RuntimeException {
            return null;
        }
    };

    public static <A, B, X extends Throwable> void someMethod() {
        transformCollection(null, unaryFunction, null);
    }

    public static <A, B, X extends Throwable> void transformCollection(Collection<? extends A> input, UnaryFunction<A, B, X> transform, Collection<? super B> output) throws X {
        for (A a : input) {
            B b = transform.execute(a);
            output.add(b);
        }
    }
}

interface UnaryFunction<A, B, X extends Throwable> {
    public B execute(A a) throws X;
}

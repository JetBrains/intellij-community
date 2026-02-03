import java.util.List;

class Generics {
    public static void main( String[] args ) {
        Outer<? extends List<? extends Nested<?>>, ?> var = OuterImpl.create(); //marked red
    }

    private static interface Outer<I, O> {
    }

    private static class OuterImpl<T> implements Outer<T, T> {
        public static <T> OuterImpl<T> create() {
            return new OuterImpl<T>();
        }
    }

    private static class Nested<T> {
    }
}

class InspectorTest {
    interface Stream<T> {
        <A> A[] toArray(IntFunction<A[]> generator);
    }
    interface IntFunction<R> {
        R apply(int value);
    }

    public static void main(Stream<Object> objectStream){
        varargMethod(String[]::new,
                     objectStream.toArray(String[]::new));
    }
    public static <T> void varargMethod(IntFunction<T[]> generator,T[]... a){}
}
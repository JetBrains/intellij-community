class Test<E_OUT> {
    final <P_IN_WRAP_SINK> Sink<P_IN_WRAP_SINK> wrapSink(Sink<E_OUT> sink) {
        return null;
    }

    final <P_IN> void wrapAndCopyInto( Sink<E_OUT> sink, Spliterator<P_IN> spliterator) {
        copyInto(wrapSink(sink), spliterator);
    }

    final <P_IN_COPY> void copyInto(Sink<P_IN_COPY> wrappedSink, Spliterator<P_IN_COPY> spliterator) {

    }
    class Sink<T> {}

    interface Spliterator<A>{}
}

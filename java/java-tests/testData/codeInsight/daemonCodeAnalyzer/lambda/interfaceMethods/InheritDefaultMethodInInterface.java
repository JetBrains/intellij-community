enum StreamShape {
    REFERENCE,
    INT_VALUE,
    LONG_VALUE,
    DOUBLE_VALUE;
}

interface TerminalSink<T, R> extends Sink<T> {
    R getAndClearState();
}
interface IntConsumer {
    public void accept(int value);
}

interface Consumer<T> {
    public void accept(T t);
}

class ForEachOp<T>  {

    protected ForEachOp(TerminalSink<T, Void> sink, StreamShape shape) {}

    protected interface VoidTerminalSink<T> extends TerminalSink<T, Void> {
        default public Void getAndClearState() {
            return null;
        }
        public interface OfInt extends VoidTerminalSink<Integer>, Sink.OfInt{}
    }

    <error descr="Class 'Foo' must either be declared abstract or implement abstract method 'accept(int)' in 'OfInt'">class Foo implements VoidTerminalSink.OfInt</error> {}

    public static <T> ForEachOp<T> make(final Consumer<? super T> consumer) {
        return new ForEachOp<>((VoidTerminalSink<T>) consumer::accept, StreamShape.REFERENCE);
    }

    public static void make(final IntConsumer consumer) {
        VoidTerminalSink.OfInt accept = consumer::accept;
    }
}


interface Sink<T> extends Consumer<T> {
    default void accept(int value) {}
    interface OfInt extends Sink<Integer>, IntConsumer {
        void accept(int value);
        default void accept(Integer i) {}
    }
}
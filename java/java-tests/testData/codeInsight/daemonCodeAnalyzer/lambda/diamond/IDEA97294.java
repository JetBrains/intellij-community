import java.util.Comparator;

interface BaseStream<T> {}
interface Stream<T> extends BaseStream<T> {}
interface IntermediateOp<E_IN, E_OUT> extends StreamOp<E_IN, Node<E_OUT>> {}
interface StreamOp<E_IN, R> {}
interface StatefulOp<E_IN, E_OUT> extends IntermediateOp<E_IN, E_OUT> {}
interface TerminalOp<E_IN, R> extends StreamOp<E_IN, R> {}

interface Node<T> extends Iterable<T> {}
class SortedOp<T> implements StatefulOp<T, T> {
    public SortedOp(Comparator<? super T> comparator) {
    }
}

class Usage<Ts> {
    public <E, S extends BaseStream<E>> S pipeline(IntermediateOp<Ts, E> newOp) { return null; }
    public <R> R pipeline(TerminalOp<Ts, R> terminal) { return null;}


    public Stream<Ts> sorted(Comparator<? super Ts> comparator) {
        return pipeline(new SortedOp<>(comparator));
    }
}

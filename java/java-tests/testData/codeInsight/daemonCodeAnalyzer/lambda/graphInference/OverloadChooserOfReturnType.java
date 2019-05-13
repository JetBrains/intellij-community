class Optional<T> {}
interface TerminalOp<E_IN, R> {}

abstract class AbstractPipeline<E_OUT>{
    abstract void evaluate();
    abstract <R> R evaluate(TerminalOp<E_OUT, R> terminalOp);

    public final Optional<E_OUT> findFirst() {
        return evaluate( makeRef(true));
    }

    public static <T> TerminalOp<T, Optional<T>> makeRef(boolean mustFindFirst) {
        return null;
    }

}

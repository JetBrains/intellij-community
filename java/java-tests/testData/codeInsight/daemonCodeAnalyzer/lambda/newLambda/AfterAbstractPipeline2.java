abstract class AbstractPip {

    interface Getter<T> {
        T get();
    }

    interface Unbounded<K> {}


    abstract <P_IN> void wrap(Getter<Unbounded<P_IN>> getter);


    public void spliterator() {
       wrap(()   -> getUnbound());
    }

    private Unbounded<? extends String> getUnbound() {
        return null;
    }
}

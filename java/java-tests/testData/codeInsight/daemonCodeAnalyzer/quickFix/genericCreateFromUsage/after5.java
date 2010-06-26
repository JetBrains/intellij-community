// "Create Constructor" "true"
public class Seq<T> {
    public Seq() {}

    public Seq(T x, Seq<T> xs) {
        <selection>//To change body of created methods use File | Settings | File Templates.</selection>
    }

    static <T> Seq<T> nil () {
        return new Seq<T> ();
    }

    static <V> Seq<V> cons (V x, Seq<V> xs) {
        return new Seq<V> (x, xs);
    }
}

// "Create Constructor" "true"
public class Seq<T> {
    public Seq() {}

    static <T> Seq<T> nil () {
        return new Seq<T> ();
    }

    static <V> Seq<V> cons (V x, Seq<V> xs) {
        return new Seq<V> (x, xs)<caret>;
    }
}

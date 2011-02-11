public class Seq<T> {
    public Seq() {}

    public Seq(T x, Seq<T> xs) {
    }

    static <T> Seq<T> nil () {
        return new Seq<T> ();
    }

    static <V> Seq<V> cons (V x, Seq<V> xs) {
        return new Seq<V> (x, xs);
    }

    static void foo() {
        Seq<String> n = <ref>nil();
    }
}
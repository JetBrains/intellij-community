class Collection<T> {}
class Comparator<T> {}

public class Collections {
    public static <T> T min(Collection<? extends T> coll, Comparator<? super T> comp) {
        if (comp==null)
             return (T)<ref>min((Collection<SelfComparable>) (Collection) coll);

        return null;
    }

    public static <T extends Object & Comparable<? super T>> T min(Collection<? extends T> coll) {
        return null;
    }

    private interface SelfComparable extends Comparable<SelfComparable> {}
}

import java.util.HashSet;
import java.util.Set;

final class Pair<A, B> {
    public final A first;
    public final B second;

    public Pair(A first, B second) {
        this.first = first;
        this.second = second;
    }

    public final A getFirst() {
        return first;
    }

    public final B getSecond() {
        return second;
    }

    public static <A, B> Pair<A, B> create(A first, B second) {
        return new Pair<A, B>(first, second);
    }

}

class Test {
    final Set<String> strings = new HashSet<String>();
    final Pair<Set<String>, Set<String>> x = Boolean.TRUE.booleanValue()
            ? Pair.create(strings, strings)
            : Pair.create(((Set<String>)  null), (Set<String>) null); //these casts are not redundant
}

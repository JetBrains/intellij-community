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

    public static <A> Pair<A, A> create(A first, A second) {
        return new Pair<A, A>(first, second);
    }

}

class Test {
    final Set<String> strings = null;
    final Pair<Set<String>, Set<String>> x = Boolean.TRUE.booleanValue()
            ? Pair.create(strings, strings)
            : Pair.create(((<warning descr="Casting 'null' to 'Set<String>' is redundant">Set<String></warning>)  null), (<warning descr="Casting 'null' to 'Set<String>' is redundant">Set<String></warning>) null); //both casts are marked, but one is required for correct inference
}

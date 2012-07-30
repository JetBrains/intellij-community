interface I<K, V> {
    public V put(K k, V v);
}

interface A<T>{}
interface B<L> extends A<L>{}

interface SameArgsI<T> {
    T same(T a, T b);
}

class InferenceFromArgs {

    private static <E> void bar(A<? extends E> a, I<? super E, Integer> i) { }

    void foo(B<Integer> b) {
        bar(b, (k, v) -> {int i = k; int j = v; return Math.max(i, j);});
    }

    public static <T> SameArgsI<T> max() {
        return (a, b) -> b;
    }
}







interface I<K, V> {
    public V put(K k, V v);
}

interface A<T>{}
interface B<L> extends A<L>{}

interface SameArgsI<T> {
    T same(T a, T b);
}

class InferenceFromArgs {

    private static <E> void bar(A<E> a, I<E, Integer> i) { }
    private static <E> void bazz(I<? super E, Integer> i) { }

    void foo(B<Integer> b) {
         bar(null, (k, v) -> v);
         bar(null, null);
         bar(b, (k, v) ->  {return v;});
         bar(b, (k, v) -> {<error descr="Incompatible types. Found: 'java.lang.Integer', required: 'java.lang.String'">String i = k;</error> return v;});
         bar(b, (k, v) -> {Integer  i = k; return v;});

         bazz((k, v) -> v);
         bazz((k, v) -> {<error descr="Incompatible types. Found: 'java.lang.Object', required: 'int'">int i = k;</error> return v;});
    }

    public static <T> SameArgsI<T> max() {
        return (a, b) -> b;
    }
}

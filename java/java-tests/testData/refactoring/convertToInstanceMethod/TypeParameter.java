class C<T> {
    T get() { return null; }
    static <V> void <caret>method(C<V> c, V value, X<V> x) {
        V v = c.get();
        System.out.println(v + " " + value);
    }
}